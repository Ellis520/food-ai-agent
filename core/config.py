"""Paths, local environment, and shared meal catalogue."""
import json
from pathlib import Path
from dotenv import load_dotenv

PROJECT = Path(__file__).resolve().parents[1]
ROOT = PROJECT / 'backend'
load_dotenv(ROOT / '.env')
PLAN_OPTIONS = json.loads((ROOT.parent / 'shared' / 'meal-options.json').read_text(encoding='utf-8'))
DIST = PROJECT / 'static' / 'dist'
