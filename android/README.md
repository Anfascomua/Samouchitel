# Android background audio shell
Native Android companion for Samouchitel. It opens https://samouchitel.pages.dev and exposes a Javascript bridge named AndroidAudio.
The bridge synthesizes temporary WAV files with the device TTS engine and plays them in a foreground Media3 service so playback continues with the screen locked.
Open this folder in Android Studio and build the app module.