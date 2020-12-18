# AudioDicer

**AudioDicer** is a Java Class built to efficiently produce a continuously varying sound stream from a small sound asset. In creating soundscapes, there is often a need for a continuous sound, for example, a flowing brook, or a crackling campfire. Continuous playback is typically achieved by looping a sound asset. If the loop is too short, the repetition can become annoyingly apparent to the User. To prevent this, longer sound files are used, but the memory costs of doing so can quickly add up.

With **AudioDicer**, small portions of audio are randomly selected from a cue and strung together to provide continuously varying audio. A scant handful of seconds of data can serve as the basis for a continuously-playing cue with no discernable looping.

### Usage

All the required functionality has been crammed into a single class. So, you can simply copy and paste the one class into your project.

#### Instantiating

```java
AudioDicer audioDicer = new AudioDicer();
```

#### Loading

The internal audio data is stored as stereo channels of signed, normalized PCM floats held in a two dimensional array. The PCM data can be either loaded directly or from a _.wav_ file.

* Loading PCM:

```java
loadAudioDataWithPCM(audioData, tracks);
```

where `audioData` is an array of signed normalized _floats_, and `tracks` is an _enum_ with one of two values: `Tracks.MONO`, `Tracks.STEREO`. If the data is _stereo_, the PCM is assumed to consist of complete frames, each holding first the _left_ and and then the _right_ channel value.

* Loading from .wav:

  ```java
  loadAudioDataFromWav(url);
  ```

where the _.wav_ file being addressed is assumed to use _16-bit, little-endian_ encoding.

#### Configuring

Publicly accessible properties:

* *sliceSize*:	size in frames of the playback fragment

  ```java
  audioDicer.setSliceSize(44100); // at 44100 fps, this would be one second
  ```

  The size of the audio fragment must be less than half of the total length of the stored cue.

* *overlap*: 	size in frames over which two adjacent fragments are cross-faded

  ```java
  audioDicer.setOverlap(4410); // at 44100 fps, 1/10th of a second 
  ```

  This transitional overlap must be less than half the _sliceSize_.

* *crossFadeMode*: can be set to NONE, LINEAR, SINE

  ```java
  audioDicer.setCrossFadeMode(CrossFadeMode.SINE);
  ```

  NONE: adjacent fragments are simply played one after another, can be prone to clicks arising from sound wave discontinuitites.

  LINEAR: for AB transition, volume of A decreases linearly while volume of B increases linearly, can result in a bit of "breathing" or differentiation of the contiguous cues, as the total power (apparent volume) when both cues are at 0.5 is lower than when either cue is fully playing.

  SINE: uses a sine function to maintain relatively stable power levels over the course of the transition, and is thus the smoothest sounding of the three options.  

* *volume*: 	real-time volume control, `double` value ranging 0 to 1

  ```java
  audioDicer.setVolume(0.7);
  ```

* *pan*: 		(to be implemented)

* *pitch*:		real-time pitch control of the playback rate, clamped to range from 0.25  to 4 (from 1/4 speed to 4Xs speed).

  ```java
  audioDicer.setPitch(2.5);
  ```

#### Playing the stream

Playing audio from the **AudioDicer** is achieved by reading *byte* arrays and feeding them to a `SourceDataLine`.

```java
	n = audioDicer.read(byteBuffer);
	sourceDataLine.write(byteBuffer, 0, n);
```

The usual practices for playing back via `SourceDataLine` apply. The above two lines should execute in their own thread, within a `while` loop or the equivalent. Note that the common pattern used when shipping data from an `AudioInputStream`:

```java
	while(n = audioDicer.read(buffer) != -1) // OK but questionable
```
might not be very helpful, as a functioning `AudioDicer` will always return the same number of bytes and never reach an end point. Consider, instead using `true` or a loosely coupled `boolean` (see the example code provided for one possibility).

### Example code

A [usage example](https://github.com/philfrei/AudioDicer/blob/master/src/example/AudioDicerExampleGUI.java), with a Swing GUI is provided in the example directory, along with sample assets: a brook, filtered brownian noise, and an organ chord that glissandos upwards over the range of an octave.

### Some more usage ideas

Using the two cues and the example code provided, I'd like to point out some interesting ways to use the **AudioDicer** that might not be readily apparent.

With the *brook* cue, try making three or four instances, and pitch them at 0.25, 0.5, 1, and 2. When playing them back at the same time, the brook becomes a much larger torrent. By varying the relative volumes (the greater the distance, the stronger the slower-pitched instances relative to the higher), 3D-distance rolloff can be emulated.

With the *chordgliss* cue, a classic, cartoonish "computer thinking" effect can be made by making the slice size 4000 and the overlap 1000. The "thinking" can become more "excited" dynamically, by slightly raising the pitch speed. The random notes that are played back will range within the single octave gliss I recorded. A wider range of random notes can be set as the source by recording a gliss that ranges over more pitches.

Another use to consider would be obtaining a short (a few seconds) segment of noise from the sound generators provided by Audacity, filtered to emphasize a given pitch area. *Brown noise* filtered to low frequencies (100-400 Hz region) can be quite useful ingredient to a sound scape. Then, `AudioDicer`'s volume and pitch capabilities can be used to dynamically alter the sound in real time based on game state (e.g., are we approaching a waterfall). Interesting noise-sculpting possibilities suggest themselves.

### To Do's

For the moment, I'm punting on adding provisions to handle panning. It's not clear to me what the best technique might be for altering panning given stereo assets. I'd like to experiment with the effectiveness of delay-based panning, especially for sounds that are pitched low before settling on coding this aspect. I invite suggestions and proposals.

### Licensing

This code has a BSD License. There is no charge for the use of this code. But if you do use it, I'd sure like to know about it and would be happy to provide links in this README to projects that make use of this code!

### Contact

My name: Phil Freihofner

My website is [https://adonax.com](https://adonax.com)

I will set up an email account for java-audio-related messages and post it here once it is working!

