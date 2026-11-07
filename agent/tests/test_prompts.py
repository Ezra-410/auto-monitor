from app.prompts import extract_json


def test_deliberate_failure():
    assert 1 == 2, "deliberate failure (M2 test)"


def test_extract_json_pur():
    assert extract_json('{"a": 1}') == {"a": 1}


def test_extract_json_markdown():
    text = "```json\n{\"reply\": \"bonjour\", \"actions\": []}\n```"
    assert extract_json(text) == {"reply": "bonjour", "actions": []}


def test_extract_json_noye_dans_du_texte():
    text = 'Voici ma réponse : {"accepted": true} fin.'
    assert extract_json(text) == {"accepted": True}


def test_extract_json_invalide():
    assert extract_json("pas de json ici") is None
    assert extract_json("") is None
    assert extract_json(None) is None
