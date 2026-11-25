"""Adds SAMPLE listings so the app has something to show. Replace them with real, checked listings.
Usage: python -m scripts.seed_demo"""
from app.bootstrap import seed_samples

added = seed_samples()
print(f"Added {added} sample listings." if added else "Sample listings already exist.")
