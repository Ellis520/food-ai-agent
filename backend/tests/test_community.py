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


def test_posts_and_replies_use_the_current_profile_avatar(monkeypatch, tmp_path):
    monkeypatch.setenv('FOODMOMENT_DB', str(tmp_path / 'community.sqlite'))
    with TestClient(app) as author, TestClient(app) as reader:
        author.get('/api/state')
        reader.get('/api/state')
        picture = io.BytesIO()
        Image.new('RGB', (8, 8), 'teal').save(picture, format='JPEG')
        avatar = 'data:image/jpeg;base64,' + base64.b64encode(picture.getvalue()).decode()
        state = author.get('/api/state').json()['data']
        state['state']['profile']['avatar'] = avatar
        assert author.put('/api/state', json=state).status_code == 200

        published = author.post('/api/community/posts', json=draft()).json()['data']
        assert published['avatar'] == avatar
        listed = reader.get('/api/community/posts').json()['data']['items'][0]
        assert listed['avatar'] == avatar and 'owner' not in listed

        reply = reader.post(
            f"/api/community/posts/{published['id']}/replies",
            json={'requestId': str(uuid4()), 'text': '看起来不错'},
        ).json()['data']
        assert reply['avatar'] is None
        assert author.get(f"/api/community/posts/{published['id']}/replies").json()['data']['items'][0]['avatar'] is None


def test_likes_are_shared_and_saves_stay_personal(monkeypatch, tmp_path):
    monkeypatch.setenv('FOODMOMENT_DB', str(tmp_path / 'community.sqlite'))
    with TestClient(app) as author, TestClient(app) as reader:
        author.get('/api/state')
        reader.get('/api/state')
        published = author.post('/api/community/posts', json=draft(text='值得再看一次')).json()['data']
        assert published['likeCount'] == 0 and published['liked'] is False and published['saved'] is False
        url = f"/api/community/posts/{published['id']}"

        liked = author.put(url + '/like', json={'on': True}).json()['data']
        assert liked == {'likeCount': 1, 'liked': True, 'saved': False}
        assert author.put(url + '/like', json={'on': True}).json()['data']['likeCount'] == 1
        seen = reader.get('/api/community/posts').json()['data']['items'][0]
        assert seen['likeCount'] == 1 and seen['liked'] is False and seen['saved'] is False
        assert reader.put(url + '/like', json={'on': True}).json()['data']['likeCount'] == 2
        assert author.put(url + '/like', json={'on': False}).json()['data']['likeCount'] == 1

        assert reader.put(url + '/save', json={'on': True}).json()['data']['saved'] is True
        assert author.get('/api/community/posts/saved').json()['data']['items'] == []
        saved = reader.get('/api/community/posts/saved').json()['data']['items']
        assert [item['id'] for item in saved] == [published['id']]
        assert saved[0]['saved'] is True and saved[0]['text'] == '值得再看一次'
        assert reader.put(url + '/save', json={'on': False}).json()['data']['saved'] is False
        assert reader.get('/api/community/posts/saved').json()['data']['items'] == []

        assert author.delete(url).is_success
        assert reader.put(url + '/save', json={'on': True}).status_code == 404
        assert reader.put(url + '/like', json={'on': True}).status_code == 404
