import requests as r
import json as j

#URL = "https://francescomecca.eu/apollon/"
URL = "http://localhost:44448"

# resp = r.post("http://localhost:8080", data=j.dumps({"user":"mario","password":"rossi", "action":"search", "keys":"kyuss gardenia"}))
# print(resp.text)
# uri = j.loads(resp.text)["values"][2]["uri"] # gardenia
# resp = r.post("http://localhost:8080", data=j.dumps({"user":"mario","password":"rossi", "action":"new-song", "uri": uri, "quality":"high"}))
# print(resp.text)
# print("now could play the file")

# inp = input(" Wanna delete the file? ")
# if inp == 'y' or inp == 'yes':
#     resp = r.post("http://localhost:8080", data=j.dumps({"user":"mario","password":"rossi", "action": "song-done", "uri": uri, "quality":"high"}))
#     print(resp.text)

def allgenre():
    resp = r.post(URL, data=j.dumps({"user":"mario","password":"rossi", "action": "all-by-genre"}))
    return resp

def allalbums():
    resp = r.post(URL, data=j.dumps({"user":"mario","password":"rossi", "action": "all-by-album"}))
    return resp

def allaartist():
    resp = r.post(URL, data=j.dumps({"user":"mario","password":"rossi", "action": "all-by-artist"}))
    return resp

def singlealbum(uri):
    resp = r.post(URL, data=j.dumps({"user":"mario","password":"rossi", "action": "album", "key":uri}))
    return resp

def singleartist(uri):
    resp = r.post(URL, data=j.dumps({"user":"mario","password":"rossi", "action": "artist", "key":uri}))
    return resp

def singlegenre(key):
    resp = r.post(URL, data=j.dumps({"user":"mario","password":"rossi", "action": "genre", "key":key}))
    return resp

def newplaylist(title, uris):
    resp = r.post(URL, data=j.dumps({"user":"mario","password":"rossi", "action": "new-playlist", "title": title, "uris": uris}))
    return resp
def delplaylist(title):
    resp = r.post(URL, data=j.dumps({"user":"mario","password":"rossi", "action": "remove-playlist", "title": title}))
    return resp

def listplaylists(user):
    resp = r.post(URL, data=j.dumps({"user":"mario","password":"rossi", "action": "list-playlists", "user": user}))
    return resp

def getplaylist(title):
    resp = r.post(URL, data=j.dumps({"user":"mario","password":"rossi", "action": "get-playlist", "title": title}))
    return resp

def addtoplaylist(title, uris):
    resp = r.post(URL, data=j.dumps({"user":"mario","password":"rossi", "action": "modify-playlist", "playlist-action": "add", "title": title, "uris": uris}))
    return resp

def removefromplaylist(title, uris):
    resp = r.post(URL, data=j.dumps({"user":"mario","password":"rossi", "action": "modify-playlist", "playlist-action": "remove", "title": title, "uris": uris}))
    return resp

def newsong(uri):
    resp = r.post(URL, data=j.dumps({"user":"mario","password":"rossi", "action": "new-song", "uri": uri, "quality": "medium"}))
    return resp
    
def conversionstatus(uri):
    resp = r.post(URL, data=j.dumps({"user":"mario","password":"rossi", "action": "conversion-status", "uri": uri}))
    return resp
    
