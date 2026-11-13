import os
from pathlib import Path

from app.core.config import Settings
from app.main import create_app

app = create_app(
    Settings(public_origin=os.environ["ACBOOKS_E2E_ORIGIN"]),
    database_path=Path(os.environ["ACBOOKS_E2E_DATABASE"]),
    web_dist_path=Path(os.environ["ACBOOKS_E2E_WEB"]),
    covers_dir=Path(os.environ["ACBOOKS_E2E_COVERS"]),
    library_dir=Path(os.environ["ACBOOKS_E2E_LIBRARY"]),
)
