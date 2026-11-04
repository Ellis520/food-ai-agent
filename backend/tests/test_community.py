import base64
import io
from uuid import uuid4

from fastapi.testclient import TestClient
from PIL import Image

from app.main import app


def draft(**values):
    return {'requestId': str(uuid4()), 'category': 'share', 'text': '今天去食堂吃饭', **values}


def test_store_reviews_match_branch_and_exclude_invitations(monkeypatch, tmp_path):
    monkeypatch.setenv('FOODMOMENT_DB', str(tmp_path / 'community.sqlite'))
    with TestClient(app) as client:
        assert client.get('/api/state').is_success
        for post in (
            draft(category='recommend', place='校园面馆（南门店）', text='店名匹配'),
            draft(text='校园面馆(南门店)的面很筋道'),
            draft(place='校园面馆（北门店）', text='另一分店，与校园面馆（南门店）不同'),
            draft(place='校园面馆', text='只写了品牌'),
            draft(category='buddy', place='校园面馆（南门店）', meetup='周五中午'),
        ):
            assert client.post('/api/community/posts', json=post).is_success

        response = client.get('/api/community/posts', params={'store': '校园面馆 (南门店)'})
        assert response.is_success
        assert [item['text'] for item in response.json()['data']['items']] == [
            '校园面馆(南门店)的面很筋道', '店名匹配',
        ]


def test_shared_feed_search_images_retries_and_ownership(monkeypatch, tmp_path):
    monkeypatch.setenv('FOODMOMENT_DB', str(tmp_path / 'community.sqlite'))
    with TestClient(app) as author, TestClient(app) as reader:
        assert reader.get('/api/community/posts').status_code == 401
        for client in (author, reader):
            assert client.get('/api/state').is_success

        picture = io.BytesIO()
        with Image.new('RGB', (12, 8), 'orange') as img:
            img.save(picture, format='JPEG', comment=b'private photo comment')
        encoded = base64.b64encode(picture.getvalue()).decode()
        review = draft(category='recommend', food='叉烧饭', place='东区食堂', images=[encoded])
        first = author.post('/api/community/posts', json=review)
        assert first.is_success
        published = first.json()['data']
        assert published['mine'] and published['imageCount'] == 1
        assert author.post('/api/community/posts', json=review).json()['data']['id'] == published['id']

        matches = reader.get('/api/community/posts', params={'category': 'recommend', 'q': '东区'}).json()['data']['items']
        assert len(matches) == 1 and matches[0]['food'] == '叉烧饭' and not matches[0]['mine']
        assert 'owner' not in matches[0]
        assert reader.get('/api/community/posts', params={'category': 'avoid', 'q': '叉烧饭'}).json()['data']['items'] == []
        post_url = f"/api/community/posts/{published['id']}"
        image_url = post_url + '/images/0'
        image = reader.get(image_url)
        assert image.headers['Content-Type'] == 'image/jpeg'
        assert b'private photo comment' not in image.content
        assert reader.delete(post_url).status_code == 404

        assert author.delete(post_url).is_success
        assert reader.get(image_url).status_code == 404
        assert reader.get('/api/community/posts').json()['data']['items'] == []


def test_invalid_posts_leave_no_rows_and_buddy_replies_are_shared(monkeypatch, tmp_path):
    monkeypatch.setenv('FOODMOMENT_DB', str(tmp_path / 'community.sqlite'))
    with TestClient(app) as author, TestClient(app) as reader:
        author.get('/api/state')
        reader.get('/api/state')
        for invalid in (draft(category='avoid'), draft(category='buddy', place='饭堂'), draft(images=['not a photo'])):
            assert author.post('/api/community/posts', json=invalid).status_code == 422
        assert author.get('/api/community/posts').json()['data']['items'] == []
        invitation = author.post('/api/community/posts', json=draft(category='buddy', place='东区食堂门口', meetup='周五 12:10'))
        assert invitation.is_success
        post_url = f"/api/community/posts/{invitation.json()['data']['id']}"
        response = reader.post(post_url+'/replies', json={'requestId': str(uuid4()), 'text': '我想一起！'})
        assert response.is_success
        reply_id = response.json()['data']['id']
        replies = author.get(post_url+'/replies').json()['data']['items']
        assert len(replies) == 1 and replies[0]['text'] == '我想一起！' and not replies[0]['mine']
        assert author.delete(post_url+f'/replies/{reply_id}').status_code == 404
        assert author.get('/api/community/posts').json()['data']['items'][0]['replyCount'] == 1
        assert author.delete(post_url).is_success
        assert reader.get(post_url+'/replies').status_code == 404
        assert reader.post(post_url+'/replies', json={'requestId': str(uuid4()), 'text': '迟到了'}).status_code == 404
