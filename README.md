## About

This is a demonstration for Kaldi on Android

## Usage

Simply import the project into Android Studio and run. It will listen for the audio and dump the transcription.

To use this library in your application simply modify the demo according to your needs - add kaldi-android aar
to dependencies, update the model and modify java UI code accodring to your needs.

## Development

This is just a demo project, the main setup to compile vosk-android
library AAR is available at [vosk-api](http://github.com/alphacep/vosk-api). Check
compilation instructions there as well as development plans.

## Languages

Models for different languages (English, Chinese, Russian) are available in
[Models](https://github.com/alphacep/vosk-api/blob/master/doc/models.md) section. To use the model unpack it into
```kaldi-android-demo/models/src/main/assets/sync/model-android```. More languages gonna be ready soon.

## Updating grammar and language model

To run on android model has to be sufficiently small, we recommend to check model sizes in the demo to figure out what should be the size of the model. If you want to update the grammar or the acoustic model, check [vosk-api documentation](https://github.com/alphacep/vosk-api/blob/master/doc/adapation.md).
