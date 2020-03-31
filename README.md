# Apollon Server

## Configuration File

TODO

## Routing

There are two possible routes:

1. https://server.com/file/<longsha>.ogg that allows the streaming of a song that was requested
2. https://server.com/ with a Request-Json in the body of the request.
3. all the other routes result in 404


## Request / Response Json

### Search

```
{
    "user": "mario",
    "password": "rossi",
    "action": "search",
    "keys": "kyuss gardenia"
}

{
  "response": "search",
  "values": [
    {
      "album": "Sons of Kyuss",
      "albumArtist": "",
      "artist": "Sons of Kyuss",
      "composer": "",
      "date": "1990",
      "disc": "",
      "genre": "Rock",
      "performer": "",
      "title": "Deadly Kiss",
      "track": "1",
      "type": "song",
      "uri": "Kyuss Discography [FLAC] [Stoner Rock, Psychedelic]/Albums/(1990) Sons of Kyuss/01 Deadly Kiss.flac"
    },
    {
      "album": "Sons of Kyuss",
      "albumArtist": "",
      "artist": "Sons of Kyuss",
      "composer": "",
      "date": "1990",
      "disc": "",
      "genre": "Rock",
      "performer": "",
      "title": "Window of Souls",
      "track": "2",
      "type": "song",
      "uri": "Kyuss Discography [FLAC] [Stoner Rock, Psychedelic]/Albums/(1990) Sons of Kyuss/02 Window of Souls.flac"
    },
    ... // many other objects
  ]
}
```

### Play Song

```
{
    "user": "mario",
    "password": "rossi",
    "action": "new-song",
    "uri": "/album/folder/song.flac",
    "quality": "medium"
}

{
    "response": "new-song",
    "uri": "http://server.com/file/<longsha>.ogg",
    "metadata": { /* Json Object, See metadata section */ }
}
```

### Done Playing Song

```
{
    "user": "mario",
    "password": "rossi",
    "action": "song-done",
    "uri": "/album/folder/song.flac",
    "quality": "medium"
}

{
    "response": "ok",
}
```

### Get List of All Artists

```
{
    "user": "mario",
    "password": "rossi",
    "action": "all-by-artists",
}
{
{
    "response": "all-artists",
    "values": [ /* ArtistEnumerated objects, see below */ ]
}
```

ArtistEnumerated object:

```
{
    "albums": [
        {
            "title": "(1990) Sons of Kyuss",
            "img": <album cover",
            "uri": <album-uri>
        },
        ...
        ],
    "img": "https://upload.wikimedia.org/wikipedia/commons/a/ab/Kinski-live.jpg",
    "name": "Kinski"
    "#nalbums": 3
}
```
        

### Get List of All Albums

```
{
    "user": "mario",
    "password": "rossi",
    "action": "all-by-albums",
}

{
    "response": "all-albums",
    "values": [ /* AlbumEnumerated Object */ ]
}
```

AlbumEnumerated Objects

```
{
    "artist": "Makoto Kawabata",
    "img": "http://coverartarchive.org/release/a1e958bd-6ab6-4f53-9999-2b38406292be/22334619695-250.jpg", 
    "uri": "Acid.Mothers.Temple/Acid Mothers Temple/Makoto Kawabata/(2005.00.00) (album) Jellyfish Rising [vbr]",
    "title": "Jellyfish Rising"
    "#nsongs": 3
}
```

### Get List of All Genres

```
{
    "user": "mario",
    "password": "rossi",
    "action": "all-by-genres",
}

{
    "response": "all-genres",
    "values":[
        "Rock",
        "Stoner Rock",
        ...
    ]
}
```


### Artist

```
{
    "user": "mario",
    "password": "rossi",
    "action": "artist",
    "key": "Kyuss"
}

{
    "response": "artist",
    "artist": {
        "name": "Kyuss",
        "img": "https://upload.wikimedia.org/wikipedia/commons/6/68/Kyuss_Lives2.JPG",
        "albums": [
            {
                "title": "(1990) Sons of Kyuss",
                "img": <album cover",
                "uri": <album-uri>
            },
            ...
        ]
    }
}
```

### Album

```
{
    "user": "mario",
    "password": "rossi",
    "action": "album",
    "key": <album uri>
}

{
    "response": "album",
    "album": {
        "title": "Welcome to Sky Valley",
        "img": "https://upload.wikimedia.org/wikipedia/commons/6/68/Kyuss_Lives2.JPG",
        "uri": "<path>,
        "artist": Kyuss,
        "songs": [{"uri": <first song uri>, "title": <first song title>},
            ...
        ]
    }
}
```

### Genre

```
{
    "user": "mario",
    "password": "rossi",
    "action": "genre",
    "key": "Progressive Rock"
}

{
    "response": "genre",
    "key": "Progressive Rock",
    "genre": {
        "artists": [
            "artist 1": [
                "album 1",
                "album 2",
                ...
            ]
            "artist 2": [ ... ],
            ...
            ]
    }
}
```

### List all:  Genre or Albums or Artists

```
{
    "user": "mario",
    "password": "rossi",
    "action": "all-by-album",
}
```

Reponse is a big dictionary of album/artist/genre objects

### Lyrics

```
{
    "user": "mario",
    "password": "rossi", 
    "action": "lyrics",
    "artist":"the knife", // lowecase or upper case does not matter
    "song": "Forest Families"
}

{
    "song":"Forest Families",
    "artist":"the Knife",
    "lyrics":
        "Too far away from the city\\r\\\n
        ...
        I just want your music tonight\\r\\n\\n",
    "response":"lyrics"
}
```

### Error

Whenever an operation is invalid

```
{
    "response": "error",
    "msg": "explanation of the error"
}
```

### Metadata

Used whenever needed to represent song metadata

```
{
  "media": {
    "@ref": "/media/asparagi/vibbra/Kyuss Discography/EPs, Singles, & Splits/(1994) Demon Cleaner/06 Gardenia (Live).flac",
    "track": [
      {
        "@type": "General",
        "AudioCount": "1",
        "FileExtension": "flac",
        "Format": "FLAC",
        "FileSize": "43451022",
        "Duration": "406.760",
        "OverallBitRate_Mode": "VBR",
        "OverallBitRate": "854578",
        "StreamSize": "0",
        "Title": "Gardenia (Live)",
        "Album": "Demon Cleaner",
        "Track": "Gardenia (Live)",
        "Track_Position": "6",
        "Track_Position_Total": "8",
        "Performer": "Kyuss",
        "Genre": "Stoner Rock",
        "Recorded_Date": "1994",
        "File_Modified_Date": "UTC 2015-11-19 08:18:48",
        "File_Modified_Date_Local": "2015-11-19 09:18:48"
      },
      {
        "@type": "Audio",
        "Format": "FLAC",
        "Duration": "406.760",
        "BitRate_Mode": "VBR",
        "BitRate": "854398",
        "Channels": "2",
        "ChannelPositions": "Front: L R",
        "ChannelLayout": "L R",
        "SamplingRate": "44100",
        "SamplingCount": "17938116",
        "BitDepth": "16",
        "Compression_Mode": "Lossless",
        "StreamSize": "43441876",
        "StreamSize_Proportion": "0.99979",
        "Encoded_Library": "reference libFLAC 1.2.1 20070917",
        "Encoded_Library_Name": "libFLAC",
        "Encoded_Library_Version": "1.2.1",
        "Encoded_Library_Date": "UTC 2007-09-17"
      }
    ]
  }
}
```

### Lyrics
```
{
 'action': 'lyrics',
 'artist': 'Nirvana',
 'password': 'rossi',
 'song': 'smell like teen spirit',
 'user': 'mario'
 }

```
```
{
    "song":"smell like teen spirit",
    "artist":"Nirvana",
    "lyrics":"Load up on guns and bring your friends\\r\\nIt\'s fun to lose and to pretend\\r\\n
    ...
    A denial\\r\\nA denial\\r\\nA denial\\r\\nA denial\\r\\nA denial\\r\\nA denial\\r\\nA denial\\r\\n\\n",
    "response":"lyrics"
 }
 ```

### Playlists

These are the possible commands to operate on playlists:

1. Create new playlist, specifying if any songs to add to add to the playlist
2. Add songs to a playlist
3. Remove songs from a playlist
4. Get a playlist from its title and user
5. Remove a playlist from its title and user
6. List all playlists for a user

##### 1. Create playlist

```
{
	"user": "mario",
	"password":"rossi", 
	"action": "new-playlist", 
	"title": <title of playlist>, 
	"uris": <uris>   /// Uris could be empty 
}
```
```
{
	"response":"ok"
}
```

##### 2. Add songs

```
{
	"user": "mario",
	"password":"rossi", 
	"action": "modify-playlist", 
	"playlist-action": "add",
	"title": <title of playlist>, 
	"uris": <uris>
}
```
```
{
	"response":"ok"
}
```

##### 3. Remove songs

```
{
	"user": "mario",
	"password":"rossi", 
	"action": "modify-playlist", 
	"playlist-action": "remove",
	"title": <title of playlist>, 
	"uris": <uris>
}
```
```
{
	"response":"ok"
}
```

##### 4. Get a playlist

```
{
	"user": "mario",
	"password": "rossi", 
	"action": "get-playlist", 
	"title": <title>
}
```

```
{
	"result": {
		"title": "title",
		"#nsongs": 1,
		"uris": [
					{
						"uri": "[1997.11, #456 326-2] 12 Concerti Grossi Op. 6 [2 CD, compil.]/CD2/17. 6. Minuetto_ Vivace.flac",
						"artist": "I Musici",
						"album": "Concerto No. 9 in F",
						"title": "6. Minuetto: Vivace",
						"genre": "",
						"performer": "",
						"composer": "Arcangelo Corelli",
						"track": "17",
                        "img": <img>
					}
				]
		},
	"response": "get-playlist"}
 ```

##### 5. Remove a playlist

```
{
	"user": "mario",
	"password": "rossi", 
	"action": "remove-playlist", 
	"title": <title>
}
```

```
{
	"response":"ok"
}
```

##### 6. List all playlists

```
{
	"user": "mario",
	"password": "rossi", 
	"action": "list-playlists", 
	"title": <title>
}
```

```
{
	"result": [
		{
			"title": "title",
			"#nsongs": 1,
			"uris": [
				{
					"album": "Concerto No. 9 in F",
					"albumArtist": "",
					"artist": "I Musici",
					"composer": "Arcangelo Corelli",
					"date": "1997",
					"disc": "2",
					"genre": "",
					"performer": "",
					"title": "5. Adagio",
					"track": "16",
					"type": "song",
					"uri": "1997.11, #456 326-2 12 Concerti Grossi Op. 6 [2 CD, compil.]/CD2/16. 5. Adagio.flac",
                    "img": <img>
				}
			]
		}
	],
	"response": "list-playlists"
 }

``` 

##### 7. Query for song conversion status

In this case the URI field refers to the file queried in the form: "/file/<sha>.mp3", not the uri of the song of the database.

```
{
	"user": "mario",
	"password": "rossi", 
	"action": "conversion-status", 
	"uri": <uri>
}
```

The possible two results are:
```
{
    "response": "conversion-status",
	"result": "done"
}
```

```
{
    "response": "conversion-status",
	"result": "ongoing"
}
```
