import requests as r
import json as j

resp = r.post("http://localhost:8080", data=j.dumps({"user":"mario","password":"rossi", "action":"search", "keys":"kyuss gardenia"}))
print(resp.text)
uri = j.loads(resp.text)["values"][2]["uri"] # gardenia
resp = r.post("http://localhost:8080", data=j.dumps({"user":"mario","password":"rossi", "action":"new-song", "uri": uri, "quality":"high"}))
print(resp.text)
print("now could play the file")

inp = input(" Wanna delete the file? ")
if inp == 'y' or inp == 'yes':
    resp = r.post("http://localhost:8080", data=j.dumps({"user":"mario","password":"rossi", "action": "song-done", "uri": uri, "quality":"high"}))
    print(resp.text)
