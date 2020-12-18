/*
 * This file is part of AudioDicer, 
 * Copyright 2020 Philip Freihofner.
 *  
 * Redistribution and use in source and binary forms, with or 
 * without modification, are permitted provided that the 
 * following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above 
 * copyright notice, this list of conditions and the following 
 * disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above 
 * copyright notice, this list of conditions and the following 
 * disclaimer in the documentation and/or other materials 
 * provided with the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of 
 * its contributors may be used to endorse or promote products 
 * derived from this software without specific prior written 
 * permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR 
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT 
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR 
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, 
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.adonax.audiodicer;

import java.io.IOException;
import java.net.URL;
import java.util.Random;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

public class AudioDicer {
	
	public enum Tracks {MONO, STEREO};
	
	private final int VOLUME_STEPS = 1024;
	private final int SPEED_STEPS = 1024 * 4;
	private final int PAN_STEPS = 1024;
	
	private boolean running;
	public boolean getRunning() { return running; }
	
	// Internal audio format is normalized signed stereo pcm, [2][frames]
	// where [0] = left and [1] = right track.
	private float[][] audioData;
	private int audioFrames; // count of frames
	private Cursor cursor; // tool used to iterate through the audioData
	
	////////// Dicing Parameters \\\\\\\\\\\
	private int sliceSize, newSliceSize;
	public void setSliceSize(int sliceSize) throws IllegalArgumentException {
		if (isProposedSliceSizeOK(sliceSize)) {
			this.newSliceSize = sliceSize;	
			if (!running) {
				this.sliceSize = sliceSize;
			}
		} else {
			throw new IllegalArgumentException("New sliceSize rejected: " + sliceSize);
		}
	}
	public int getSliceSize() {return sliceSize;}
	
	private int overlap, newOverlap;
	public void setOverlap(int overlap)	throws IllegalArgumentException {
		// rule: overlap must be no larger than 50% sliceSize
		
		if (overlap * 2 < sliceSize)
		{
			this.newOverlap = overlap;
			if (!running) {
				this.newOverlap = overlap;
			}
		} else {
			throw new IllegalArgumentException("New overlap rejected:" + overlap);
		}
	}
	public int getOverlap() {return overlap;}

	public enum CrossFadeMode { NONE, LINEAR, SINE}
	private CrossFadeMode crossfadeMode, newCfMode;
	public void setCrossFadeMode(CrossFadeMode cfMode) {
		newCfMode = cfMode;
		if (! running) {
			this.crossfadeMode = cfMode;
		}
	}

	private float crossfadeRatio, cfRatioDelta;
	
	/////////  read management  \\\\\\\\\
	private int countdownIdx;
	private int stateswitch; // 0=cursorA, 1=AtoB transition
							//  2=cursorB, 3=BtoA transition
	private float[] pcmA, pcmB, pcmOut;
	
	private Random random;
	
	/*
	 *  Default comb filter padding = sample rate * 30 millis
	 *  or about 1/33 of a second, to be on the safe side.
	 *  Comb filtering is usually considered to end with delays 
	 *  of around 25 millis.  
	 */
	private int combFilterPadding = (int)(44100 * 0.03);
	public void setCombFilterPadding(int combFilterPadding) {
		this.combFilterPadding = combFilterPadding;
	}
	public int getCombFilterPadding() {
		return combFilterPadding;
	}
	
	//////////////////// Constructor ///////////////////
	public AudioDicer() {
		cursor = new Cursor();
		cursor.reset();
		pcmA = new float[2];
		pcmB = new float[2];
		pcmOut = new float[2];
		random = new Random();
	}
	
	///////////////////// LOAD Data /////////////////////
	// allow option of loading PCM data directly
	/////////////////////////////////////////////////////
	public void loadAudioDataWithPCM(float[] audioData, Tracks tracks) {
		
		int n = audioData.length;
		switch (tracks) {
			case MONO:
				// Convert to a stereo file to conform with
				// the internal format.
				this.audioData = new float[2][n];
				for (int i = 0; i < n; i++) {
					this.audioData[0][i] = audioData[i];
					this.audioData[1][i] = audioData[i];
				}
				break;
			case STEREO:
				this.audioData = new float[2][n/2];
				for (int i = 0; i < n; i += 2) {
					this.audioData[0][i/2] = audioData[i];
					this.audioData[1][i/2] = audioData[i + 1];
				}
		}
	}
	
	// load from URL
	public void loadAudioDataFromWAV(URL url) 
			throws UnsupportedAudioFileException, IOException {
		
		AudioInputStream ais = AudioSystem.getAudioInputStream(url);
		AudioFormat format = ais.getFormat();
		
		// Is it Mono or Stereo?
		int numberOfTracks = format.getChannels();
		Tracks tracks;
		switch (numberOfTracks) {
			case 1: 
				tracks = Tracks.MONO;
				break;
			case 2: 
				tracks = Tracks.STEREO;
				break;
			default:
				System.out.println("Error: audio file has an unsupported "
						+ "number of channels:" + numberOfTracks);
				return;
		}
	
		audioFrames = (int)ais.getFrameLength();
		audioData = new float[2][audioFrames];
		
		int READ_BUFFER_SIZE = 1024 * 8;
		byte[] readBuffer = new byte[READ_BUFFER_SIZE];
		int bytesRead = 0;
		int pcmIdx = 0;
		
		while((bytesRead = ais.read(readBuffer, 0, READ_BUFFER_SIZE)) != -1) {
			
			int ii = 0;
			while (ii < bytesRead) {
				audioData[0][pcmIdx] = 
							( readBuffer[ii++] & 0xff )
							| ( readBuffer[ii++] << 8 ) ;
				
				if (tracks == Tracks.MONO) {
					audioData[1][pcmIdx] = audioData[0][pcmIdx];
				} else { // STEREO
					audioData[1][pcmIdx] = 
							( readBuffer[ii++] & 0xff )
							| ( readBuffer[ii++] << 8 ) ;
				}
				
				pcmIdx++;
			}
		}

		// normalize
		for (int i = 0; i < audioFrames; i++)
		{
			audioData[0][i] /= 32767f;
			audioData[1][i] /= 32767f;
		}
	}
	
	public void start() {
		countdownIdx = sliceSize - (2 * overlap);
		// Q: should we start from 0, or from a random spot?
		cursor.idxA = getNextStart(audioData[0].length - 1);
		running = true;
	}
	
	////////////////// Cursor Management ///////////////////	
	public void setVolume(double volume) {	
		cursor.targetVolume = (float)Math.min(1, Math.max(0, volume));
		cursor.targetVolumeIncr = 
				(cursor.targetVolume - cursor.volume) / VOLUME_STEPS;
		cursor.targetVolumeSteps = VOLUME_STEPS;
	};	
	public double getVolume() {
		return cursor.volume;
	}

	public void setPan(double pan) {		
		cursor.targetPan = (float)Math.min(1, Math.max(-1, pan));
		cursor.targetPanIncr = (cursor.targetPan - cursor.pan) / PAN_STEPS;
		cursor.targetPanSteps = PAN_STEPS;
	};

	public double getPan() { return cursor.pan; }

	public void setSpeed(double speed)
	{
		if (!isProposedSpeedOK(speed)) {
			System.out.println("speed clamped at: " + cursor.speed);
			return;
		}
		
		if (running) {
			cursor.targetSpeed = (float)Math.min(4, Math.max(0.25, speed));
			cursor.targetSpeedIncr = 
					(cursor.targetSpeed - cursor.speed) / SPEED_STEPS;
			cursor.targetSpeedSteps = SPEED_STEPS;
		} else {
			cursor.speed = (float)speed;
		}
	};
		
	public double getSpeed() { return cursor.speed;	}

	// Intention: keep all mutable values for the read cursor together.
	// TODO QUESTION: should countdown vars also be here? Maybe so!
	private class Cursor {
		float idxA, idxB;
		float speed;
		float volume;
		float pan;

		float targetSpeed;
		float targetSpeedIncr;
		int targetSpeedSteps;
		
		float targetVolume;
		float targetVolumeIncr;
		int targetVolumeSteps;
		
		float targetPan;
		float targetPanIncr;
		int targetPanSteps;
				
		/*
		 * Used to restore initial, default settings.
		 */
		void reset() {
			idxA = 0;
			idxB = 0;
			speed = 1;  // [0.25..4]
			volume = 0; // [0..1]
			pan = 0;    // [-1..1]
			
			targetSpeedSteps = 0;
			targetVolumeSteps = 0;
			targetPanSteps = 0;
		}
	}
	
	///////////// READING DATA /////////////
	
	public int read(byte[] buffer) {
		
		if (!running) {
			// TODO : throw illegal state?
			System.out.println("Cannot read. Call start() method to run.");
			return -1;
		}

		int bufferIdx = 0;
		
		// Loop to load the data buffer, a one-dimensional array, 4 bytes per frame
		for (int i = 0, n = buffer.length / 4; i < n; i++) {
			
			if (cursor.targetSpeedSteps-- > 0)
			{
				cursor.speed += cursor.targetSpeedIncr;
			}
			
			if (cursor.targetVolumeSteps-- > 0)
			{
				cursor.volume += cursor.targetVolumeIncr;
			}
			
			pcmOut = getNextAudio();
			pcmOut[0] *= cursor.volume;
			pcmOut[1] *= cursor.volume;
				
			// Section for converting to bytes
			bufferIdx = i * 4;
			
			float pcmVal = pcmOut[0] * 32767;
			buffer[bufferIdx] = (byte)pcmVal;
			buffer[bufferIdx + 1] = (byte)((int)pcmVal >> 8 );
			pcmVal = pcmOut[1] * 32767;
			buffer[bufferIdx + 2] = (byte)pcmVal;
			buffer[bufferIdx + 3] = (byte)((int)pcmVal >> 8 );
		}
		
		return bufferIdx + 4;
	}
	
	private float[] getNextAudio() {	
		countdownIdx--;
		if (countdownIdx < 1)
		{
			// time to switch to next state
			stateswitch++;
			stateswitch &= 3; // this "wraps" 4 back to 0 
			
			if ((stateswitch % 2) == 1) // we've entered into CROSS-FADING	
			{
				// This is a safe place to update new (loosely coupled)
				// values for the slicing operation.
				if (overlap != newOverlap) {
					overlap = newOverlap;
//					System.out.println("new overlap: " + overlap);
				}
				if (sliceSize != newSliceSize) {
					sliceSize = newSliceSize;
//					System.out.println("new slicesize: " + sliceSize);
				}
				if (crossfadeMode != newCfMode) {
					crossfadeMode = newCfMode;
//					System.out.println("new crossfade: " + crossfadeMode.name());
				}
				
				countdownIdx = overlap;
				crossfadeRatio = 0;
				cfRatioDelta = 1f / overlap;
				if (stateswitch == 1) // stream A fades, B grows
				{
					cursor.idxB = getNextStart((int)cursor.idxA);
				}		
				else // stateswitch == 3, stream B fades, A grows
				{
					cursor.idxA = getNextStart((int)cursor.idxB);
				}
			}
			else { // CROSS-FADE just ended, set up to play slice
				countdownIdx = sliceSize - (2 * overlap);
			}
		}
		
		// get next PCM
		switch (stateswitch)
		{
			case 0: 
			{
				cursor.idxA += cursor.speed;
				pcmOut = getPCMVal(pcmOut, cursor.idxA);
				break;
			}
			case 1: 
			{
				cursor.idxA += cursor.speed;
				cursor.idxB += cursor.speed;
				pcmA = getPCMVal(pcmA, cursor.idxA);
				pcmB = getPCMVal(pcmB, cursor.idxB);
				crossfadeRatio += cfRatioDelta;
				pcmOut = crossfade(pcmOut, pcmA, pcmB, crossfadeRatio,
						crossfadeMode);
				break;
			}
			case 2: 
			{
				cursor.idxB += cursor.speed;
				pcmOut = getPCMVal(pcmOut, cursor.idxB);
				break;
			}
			case 3: 
			{
				cursor.idxA += cursor.speed;
				cursor.idxB += cursor.speed;
				pcmA = getPCMVal(pcmA, cursor.idxA);
				pcmB = getPCMVal(pcmB, cursor.idxB);
				crossfadeRatio += cfRatioDelta;
				pcmOut = crossfade(pcmOut, pcmB, pcmA, crossfadeRatio,
						crossfadeMode);
				break;
			}
		}

		return pcmOut;
	}

	//////// UTILITIES ////////
	private float[] getPCMVal(float[] pcmFrame, float idx) {
		// linear interpolation algo is used here
		final int intIdx = (int)idx;
		pcmFrame[0] = audioData[0][intIdx + 1] * (idx - intIdx) 
				+ audioData[0][intIdx] * ((intIdx + 1) - idx);
		
		pcmFrame[1] = audioData[1][intIdx + 1] * (idx - intIdx) 
				+ audioData[1][intIdx] * ((intIdx + 1) - idx);

		return pcmFrame;
	}
		
	private final double PI_DIV_2 = Math.PI / 2;
	private float[] crossfade(float[] ab, float[] a, float[] b, float normal, 
			CrossFadeMode mode)
	{
		switch(mode) {
		case NONE:
			if (normal <= 0.5) {
				ab[0] = a[0];
				ab[1] = a[1];
			} else {
				ab[0] = b[0];
				ab[1] = b[1];
			}
			break;
		case LINEAR:
			ab[0] = a[0] * (1 - normal) + b[0] * normal;
			ab[1] = a[1] * (1 - normal) + b[1] * normal;
			break;
		case SINE:
			ab[0] = (float)(b[0] * (Math.sin(PI_DIV_2 * normal)) 
					+ a[0] * (Math.sin(PI_DIV_2 * (1 - normal))));
			ab[1] = (float)(b[1] * (Math.sin(PI_DIV_2 * normal)) 
					+ a[1] * (Math.sin(PI_DIV_2 * (1 - normal))));
			break;
		default: 
		}	
		
		return ab;
	}
	
	private int getNextStart(int currentIdx) {
		int nextStartRange;
		int pcmMidpoint;
		int adjCombFilterPad = combFilterPadding;
		
		nextStartRange = audioFrames - sliceSize;
		
		// Need to narrow the range when using faster speed.
		double maxSpeed = Math.max(cursor.speed, cursor.targetSpeed);
		if (maxSpeed > 1) {
			nextStartRange = (int)(nextStartRange / maxSpeed);
			adjCombFilterPad = (int)(combFilterPadding / maxSpeed);
		}

		int nextStart = random.nextInt(nextStartRange);
		pcmMidpoint = nextStartRange / 2;				

		if (Math.abs(currentIdx - nextStart) < adjCombFilterPad) {
			if (nextStart < pcmMidpoint) {
				nextStart -= adjCombFilterPad;
			} else {
				nextStart += adjCombFilterPad;
			}
		}

		return nextStart;
	}
	
	private boolean isProposedSliceSizeOK(int proposedSliceSize) {
		/* RULES: 
		 * 		> After speed adjustment, range of permitted starts
		 * 		> should be at least 50% of entire cue.
		 * 		> The 50% figure is an arbitrary choice.
		 * 		> ALSO, the slice must remain at least 2X the size 
		 * 		> of the overlap.
		 */
		if (proposedSliceSize < overlap * 2) return false;
		
		int impliedSliceSize = proposedSliceSize;
		
		double adjSpeed = Math.max(cursor.speed, cursor.targetSpeed);
		if (adjSpeed > 1) {
			impliedSliceSize = 
					(int)((proposedSliceSize + 2 * combFilterPadding) * adjSpeed);
		}
		
		return (impliedSliceSize * 2 < audioFrames); 
	}
	
	private boolean isProposedSpeedOK(double proposedSpeed) {
		/* RULES: 
		 * 		> After speed adjustment, range of permitted starts
		 * 		> should be over at least 50% of entire cue.
		 * 		> The 50% figure is an arbitrary choice.
		 */
		int adjSliceSize = Math.max(sliceSize, newSliceSize);
		int impliedSliceSize = adjSliceSize;
		if (proposedSpeed > 1) {
			impliedSliceSize = (int)((adjSliceSize + 2 * combFilterPadding) * proposedSpeed);
		}

		return impliedSliceSize * 2 < audioFrames; 
	}
}