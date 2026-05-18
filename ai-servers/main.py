import os

from app.main import app


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=int(os.getenv("PYTHON_SERVER_PORT", "8081")), reload=False)
