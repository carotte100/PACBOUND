"""
Petit serveur HTTP pour stocker et relire un JSON, pense pour l'app PacLearn.

Lancement :
    python server.py [port]

Par defaut : port 8000, ecoute sur 0.0.0.0 (accessible depuis le telephone
si l'ordi et le telephone sont sur le meme reseau Wi-Fi).

Endpoints :
    GET  /data   -> renvoie le JSON stocke (objet vide {} si rien encore)
    POST /data   -> remplace le JSON stocke par le corps de la requete
                    (doit etre un JSON valide, sinon 400)
"""

import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

DATA_FILE = Path(__file__).parent / "data.json"


def read_data():
    if not DATA_FILE.exists():
        return {}
    with DATA_FILE.open("r", encoding="utf-8") as f:
        return json.load(f)


def write_data(data):
    with DATA_FILE.open("w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


class JsonStoreHandler(BaseHTTPRequestHandler):
    def _send_json(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def do_GET(self):
        if self.path != "/data":
            self._send_json(404, {"error": "not found"})
            return
        try:
            self._send_json(200, read_data())
        except json.JSONDecodeError:
            self._send_json(500, {"error": "fichier de donnees corrompu"})

    def do_POST(self):
        if self.path != "/data":
            self._send_json(404, {"error": "not found"})
            return
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length) if length else b""
        try:
            payload = json.loads(raw.decode("utf-8") or "{}")
        except json.JSONDecodeError:
            self._send_json(400, {"error": "JSON invalide"})
            return
        write_data(payload)
        self._send_json(200, payload)

    def log_message(self, fmt, *args):
        print("[server]", fmt % args)


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    server = ThreadingHTTPServer(("0.0.0.0", port), JsonStoreHandler)
    print(f"Serveur JSON demarre sur http://0.0.0.0:{port}  (GET/POST /data)")
    print("Ctrl+C pour arreter.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
