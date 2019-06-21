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
        "TODO"
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
    "action": "all-artists",
}
{
}
```

### Get List of All Albums

```
{
    "user": "mario",
    "password": "rossi",
    "action": "all-albums",
}

{
}
```

### Get List of All Genres

```
{
    "user": "mario",
    "password": "rossi",
    "action": "all-genres",
}

{
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
            "(1990) Sons of Kyuss",
            "(1991) Wretch",  
            "(1992) Blues for the Red Sun",
            "(1994) Welcome to Sky Valley",
            "(1995) ...and the Circus Leaves Town"
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
    "key": "Welcome to Sky Valley"
}

{
    "response": "album",
    "album": {
        "title": "Welcome to Sky Valley",
        "img": "https://upload.wikimedia.org/wikipedia/commons/6/68/Kyuss_Lives2.JPG",
        "uri": "<path>,
        "artist": Kyuss
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
