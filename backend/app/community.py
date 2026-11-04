"""Shared food feed, scoped to the current FoodMoment database."""

import base64
import binascii
import io
import json
import re
import sqlite3
import unicodedata
from contextlib import closing, contextmanager
from datetime import datetime, timezone
from typing import Literal
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, Request, Response
from PIL import Image, ImageOps
from pydantic import BaseModel, ConfigDict, Field, ValidationError, model_validator

Category = Literal['share', 'recommend', 'avoid', 'buddy']
IMAGE_LIMIT = 750 * 1024
SCHEMA = """
CREATE TABLE IF NOT EXISTS community_posts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner TEXT NOT NULL,
    request_id TEXT NOT NULL,
    author TEXT NOT NULL,
    category TEXT NOT NULL,
    food TEXT NOT NULL,
    place TEXT NOT NULL,
    meetup TEXT NOT NULL,
    body TEXT NOT NULL,
    created_at TEXT NOT NULL,
    UNIQUE(owner, request_id)
);
CREATE INDEX IF NOT EXISTS community_posts_category ON community_posts(category, id);
CREATE TABLE IF NOT EXISTS community_images (
    post_id INTEGER NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    jpeg BLOB NOT NULL,
    PRIMARY KEY (post_id, position)
);
CREATE TABLE IF NOT EXISTS community_replies (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    post_id INTEGER NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    owner TEXT NOT NULL,
    request_id TEXT NOT NULL,
    author TEXT NOT NULL,
    body TEXT NOT NULL,
    created_at TEXT NOT NULL,
    UNIQUE(post_id, owner, request_id)
);
CREATE INDEX IF NOT EXISTS community_replies_post ON community_replies(post_id, id);
CREATE TABLE IF NOT EXISTS community_likes (
    post_id INTEGER NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    owner TEXT NOT NULL,
    PRIMARY KEY (post_id, owner)
);
CREATE TABLE IF NOT EXISTS community_saves (
    post_id INTEGER NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    owner TEXT NOT NULL,
    created_at TEXT NOT NULL,
    PRIMARY KEY (post_id, owner)
);
CREATE INDEX IF NOT EXISTS community_saves_owner ON community_saves(owner, created_at);
"""


class PostInput(BaseModel):
    model_config = ConfigDict(extra='forbid', str_strip_whitespace=True)
    requestId: UUID
    category: Category
    text: str = Field(min_length=1, max_length=600)
    food: str = Field(default='', max_length=60)
    place: str = Field(default='', max_length=80)
    meetup: str = Field(default='', max_length=60)
    images: list[str] = Field(default_factory=list, max_length=3)

    @model_validator(mode='after')
    def required_details(self):
        if self.category in ('recommend', 'avoid') and not (self.food or self.place):
            raise ValueError('请填写菜名或店名')
        if self.category == 'buddy' and not (self.meetup and self.place):
            raise ValueError('请填写约饭时间和地点')
        return self


class ReplyInput(BaseModel):
    model_config = ConfigDict(extra='forbid', str_strip_whitespace=True)
    requestId: UUID
    text: str = Field(min_length=1, max_length=300)


class ReactionInput(BaseModel):
    model_config = ConfigDict(extra='forbid')
    on: bool


async def limited_json(request: Request, model, limit: int):
    if request.headers.get('content-type', '').split(';', 1)[0].strip().lower() != 'application/json':
        raise HTTPException(415, '请发送 JSON 数据。')
    data = bytearray()
    async for chunk in request.stream():
        if len(data) + len(chunk) > limit:
            raise HTTPException(413, '内容太大，请减少图片后再试。')
        data.extend(chunk)
    try:
        return model.model_validate_json(data)
    except ValidationError as error:
        detail = error.errors(include_input=False)[0]
        message = str(detail.get('ctx', {}).get('error', ''))
        raise HTTPException(422, message or '请检查必填项和字数限制。') from error


async def post_input(request: Request) -> PostInput:
    return await limited_json(request, PostInput, 3_200_000)


async def reply_input(request: Request) -> ReplyInput:
    return await limited_json(request, ReplyInput, 5000)


def clean_image(encoded: str) -> bytes:
    if len(encoded) > (IMAGE_LIMIT + 2) // 3 * 4:
        raise HTTPException(413, '单张图片过大，请换一张。')
    try:
        raw = base64.b64decode(encoded, validate=True)
        if len(raw) > IMAGE_LIMIT:
            raise HTTPException(413, '单张图片过大，请换一张。')
        with Image.open(io.BytesIO(raw), formats=['JPEG']) as source:
            if max(source.size) > 1280:
                raise ValueError('Image exceeds dimensions')
            with ImageOps.exif_transpose(source).convert('RGB') as pixels:
                # Save decoded pixels only, without the original file's metadata.
                pixels.info.clear()
                with io.BytesIO() as output:
                    pixels.save(output, format='JPEG', quality=82)
                    return output.getvalue()
    except (binascii.Error, OSError, ValueError, SyntaxError, Image.DecompressionBombError) as error:
        raise HTTPException(422, '图片无法读取，请重新选择 JPG、PNG 或 WebP 图片。') from error


def display_name(db, owner):
    row = db.execute('SELECT data FROM sessions WHERE id=?', (owner,)).fetchone()
    return json.loads(row['data']).get('profile', {}).get('name', '').strip() or '食刻同学'


def profile_avatars(db, owners):
    ids = list({owner for owner in owners if owner})
    if not ids:
        return {}
    marks = ','.join('?' for _ in ids)
    rows = db.execute(f'SELECT id, data FROM sessions WHERE id IN ({marks})', ids).fetchall()
    photos = {}
    prefix = 'data:image/jpeg;base64,'
    for row in rows:
        try:
            avatar = json.loads(row['data']).get('profile', {}).get('avatar')
        except (TypeError, json.JSONDecodeError):
            avatar = None
        photos[row['id']] = (
            avatar if isinstance(avatar, str) and avatar.startswith(prefix) and len(avatar) <= 100_000 else None
        )
    return photos


def post_data(row, owner, avatar):
    return {
        'id': row['id'], 'author': row['author'], 'avatar': avatar, 'category': row['category'],
        'food': row['food'], 'place': row['place'], 'meetup': row['meetup'],
        'text': row['body'], 'createdAt': row['created_at'], 'mine': row['owner'] == owner,
        'imageCount': row['image_count'], 'replyCount': row['reply_count'],
        'likeCount': row['like_count'], 'liked': bool(row['liked']), 'saved': bool(row['saved']),
    }


def reply_data(row, owner, avatar):
    return {
        'id': row['id'], 'author': row['author'], 'avatar': avatar, 'text': row['body'],
        'createdAt': row['created_at'], 'mine': row['owner'] == owner,
    }


def store_key(name: str) -> str:
    name = unicodedata.normalize('NFKC', name).casefold()
    return re.sub(r'[\s()·•-]+', '', name)


POST_SELECT = """
    SELECT p.*,
      (SELECT COUNT(*) FROM community_images WHERE post_id=p.id) AS image_count,
      (SELECT COUNT(*) FROM community_replies WHERE post_id=p.id) AS reply_count,
      (SELECT COUNT(*) FROM community_likes WHERE post_id=p.id) AS like_count,
      EXISTS(SELECT 1 FROM community_likes WHERE post_id=p.id AND owner=?) AS liked,
      EXISTS(SELECT 1 FROM community_saves WHERE post_id=p.id AND owner=?) AS saved
    FROM community_posts p
"""


def community_router(connect, session_id):
    router = APIRouter(prefix='/api/community', tags=['community'])

    @contextmanager
    def database():
        with closing(connect()) as db:
            db.row_factory = sqlite3.Row
            db.execute('PRAGMA foreign_keys=ON')
            db.executescript(SCHEMA)
            with db:
                yield db

    @router.get('/posts')
    def list_posts(request: Request, response: Response, category: Category | None = None,
                   q: str = Query(default='', max_length=80), before: int | None = Query(default=None, gt=0),
                   store: str | None = Query(default=None, min_length=1, max_length=160)):
        owner = session_id(request)
        response.headers['Cache-Control'] = 'no-store'
        term = q.strip()
        key = store_key(store) if store else ''
        with database() as db:
            if store is not None:
                # A generic brand mention cannot identify a branch. Only accept a
                # text mention without a venue when it includes the full branch name.
                has_branch = bool(re.search(r'\([^()]+\)$', unicodedata.normalize('NFKC', store).strip()))
                db.create_function('matches_store', 2, lambda place, body: bool(key) and (
                    store_key(place) == key if place.strip() else has_branch and key in store_key(body)
                ), deterministic=True)
            rows = db.execute(POST_SELECT + """
                WHERE (? IS NULL OR p.category=?)
                  AND (? IS NULL OR p.id<?)
                  AND (instr(lower(p.food), lower(?)) OR instr(lower(p.place), lower(?))
                       OR instr(lower(p.body), lower(?)))
            """ + (" AND p.category!='buddy' AND matches_store(p.place, p.body)" if store is not None else "")
                + " ORDER BY p.id DESC LIMIT 21",
                (owner, owner, category, category, before, before, term, term, term)).fetchall()
            photos = profile_avatars(db, [row['owner'] for row in rows[:20]])
        return {'data': {'items': [post_data(row, owner, photos.get(row['owner'])) for row in rows[:20]],
                         'next': rows[19]['id'] if len(rows) > 20 else None}, 'source': 'sqlite'}

    @router.post('/posts')
    def create_post(request: Request, body: PostInput = Depends(post_input)):
        owner = session_id(request)
        images = [clean_image(encoded) for encoded in body.images]
        with database() as db:
            result = db.execute("""
                INSERT INTO community_posts(owner,request_id,author,category,food,place,meetup,body,created_at)
                VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(owner,request_id) DO NOTHING
            """, (owner, str(body.requestId), display_name(db, owner), body.category,
                  body.food, body.place, body.meetup if body.category == 'buddy' else '', body.text,
                  datetime.now(timezone.utc).isoformat()))
            if result.rowcount:
                db.executemany('INSERT INTO community_images VALUES(?,?,?)',
                               [(result.lastrowid, position, image) for position, image in enumerate(images)])
            row = db.execute(POST_SELECT + ' WHERE p.owner=? AND p.request_id=?',
                             (owner, owner, owner, str(body.requestId))).fetchone()
            photo = profile_avatars(db, [owner]).get(owner)
        return {'data': post_data(row, owner, photo), 'source': 'sqlite'}

    @router.get('/posts/{post_id}/images/{position}')
    def image(post_id: int, position: int, request: Request):
        session_id(request)
        with database() as db:
            row = db.execute('SELECT jpeg FROM community_images WHERE post_id=? AND position=?',
                             (post_id, position)).fetchone()
        if row is None:
            raise HTTPException(404, '图片不存在。')
        return Response(row['jpeg'], media_type='image/jpeg',
                        headers={'X-Content-Type-Options': 'nosniff', 'Cache-Control': 'private, no-store'})

    @router.delete('/posts/{post_id}')
    def delete_post(post_id: int, request: Request):
        owner = session_id(request)
        with database() as db:
            deleted = db.execute('DELETE FROM community_posts WHERE id=? AND owner=?', (post_id, owner))
            if not deleted.rowcount:
                raise HTTPException(404, '帖子不存在或你不是发布者。')
        return {'data': {'deleted': True}, 'source': 'sqlite'}

    def reaction(db, post_id, owner):
        if not db.execute('SELECT 1 FROM community_posts WHERE id=?', (post_id,)).fetchone():
            raise HTTPException(404, '帖子已删除。')
        row = db.execute("""
            SELECT
              (SELECT COUNT(*) FROM community_likes WHERE post_id=?) AS like_count,
              EXISTS(SELECT 1 FROM community_likes WHERE post_id=? AND owner=?) AS liked,
              EXISTS(SELECT 1 FROM community_saves WHERE post_id=? AND owner=?) AS saved
        """, (post_id, post_id, owner, post_id, owner)).fetchone()
        return {'likeCount': row['like_count'], 'liked': bool(row['liked']), 'saved': bool(row['saved'])}

    @router.get('/posts/saved')
    def saved_posts(request: Request, response: Response):
        owner = session_id(request)
        response.headers['Cache-Control'] = 'no-store'
        with database() as db:
            rows = db.execute(POST_SELECT + """
                JOIN community_saves s ON s.post_id=p.id AND s.owner=?
                ORDER BY s.created_at DESC, p.id DESC
                LIMIT 50
            """, (owner, owner, owner)).fetchall()
            photos = profile_avatars(db, [row['owner'] for row in rows])
        return {'data': {'items': [post_data(row, owner, photos.get(row['owner'])) for row in rows],
                         'next': None}, 'source': 'sqlite'}

    @router.put('/posts/{post_id}/like')
    def set_like(post_id: int, request: Request, body: ReactionInput):
        owner = session_id(request)
        with database() as db:
            if not db.execute('SELECT 1 FROM community_posts WHERE id=?', (post_id,)).fetchone():
                raise HTTPException(404, '帖子已删除。')
            if body.on:
                db.execute('INSERT INTO community_likes(post_id, owner) VALUES(?,?) ON CONFLICT DO NOTHING',
                           (post_id, owner))
            else:
                db.execute('DELETE FROM community_likes WHERE post_id=? AND owner=?', (post_id, owner))
            state = reaction(db, post_id, owner)
        return {'data': state, 'source': 'sqlite'}

    @router.put('/posts/{post_id}/save')
    def set_save(post_id: int, request: Request, body: ReactionInput):
        owner = session_id(request)
        with database() as db:
            if not db.execute('SELECT 1 FROM community_posts WHERE id=?', (post_id,)).fetchone():
                raise HTTPException(404, '帖子已删除。')
            if body.on:
                db.execute("""
                    INSERT INTO community_saves(post_id, owner, created_at) VALUES(?,?,?)
                    ON CONFLICT DO NOTHING
                """, (post_id, owner, datetime.now(timezone.utc).isoformat()))
            else:
                db.execute('DELETE FROM community_saves WHERE post_id=? AND owner=?', (post_id, owner))
            state = reaction(db, post_id, owner)
        return {'data': state, 'source': 'sqlite'}

    @router.get('/posts/{post_id}/replies')
    def list_replies(post_id: int, request: Request, response: Response,
                     before: int | None = Query(default=None, gt=0)):
        owner = session_id(request)
        response.headers['Cache-Control'] = 'no-store'
        with database() as db:
            if not db.execute('SELECT 1 FROM community_posts WHERE id=?', (post_id,)).fetchone():
                raise HTTPException(404, '帖子已删除。')
            rows = db.execute("""
                SELECT * FROM community_replies WHERE post_id=? AND (? IS NULL OR id<?)
                ORDER BY id DESC LIMIT 31
            """, (post_id, before, before)).fetchall()
            shown = list(reversed(rows[:30]))
            photos = profile_avatars(db, [row['owner'] for row in shown])
        return {'data': {'items': [reply_data(row, owner, photos.get(row['owner'])) for row in shown],
                         'next': rows[29]['id'] if len(rows) > 30 else None}, 'source': 'sqlite'}

    @router.post('/posts/{post_id}/replies')
    def create_reply(post_id: int, request: Request, body: ReplyInput = Depends(reply_input)):
        owner = session_id(request)
        with database() as db:
            try:
                db.execute("""
                    INSERT INTO community_replies(post_id,owner,request_id,author,body,created_at)
                    VALUES(?,?,?,?,?,?) ON CONFLICT(post_id,owner,request_id) DO NOTHING
                """, (post_id, owner, str(body.requestId), display_name(db, owner),
                      body.text, datetime.now(timezone.utc).isoformat()))
            except sqlite3.IntegrityError as error:
                raise HTTPException(404, '帖子已删除。') from error
            row = db.execute('SELECT * FROM community_replies WHERE post_id=? AND owner=? AND request_id=?',
                             (post_id, owner, str(body.requestId))).fetchone()
            photo = profile_avatars(db, [owner]).get(owner)
        return {'data': reply_data(row, owner, photo), 'source': 'sqlite'}

    @router.delete('/posts/{post_id}/replies/{reply_id}')
    def delete_reply(post_id: int, reply_id: int, request: Request):
        owner = session_id(request)
        with database() as db:
            deleted = db.execute('DELETE FROM community_replies WHERE id=? AND post_id=? AND owner=?',
                                 (reply_id, post_id, owner))
            if not deleted.rowcount:
                raise HTTPException(404, '留言不存在或你不是发布者。')
        return {'data': {'deleted': True}, 'source': 'sqlite'}

    return router
