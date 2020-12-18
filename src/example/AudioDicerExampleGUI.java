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
package example;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URL;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.Line.Info;
import javax.sound.sampled.LineUnavailableException;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.adonax.audiodicer.AudioDicer;
import com.adonax.audiodicer.AudioDicer.CrossFadeMode;

public class AudioDicerExampleGUI {

	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable(){
            public void run(){
            	JFrame frame = new AudioDicerGUI_wSliders();
            	frame.setDefaultCloseOperation(
            			JFrame.EXIT_ON_CLOSE);
            	frame.setVisible(true);
            }
        });
	}	
}

final class AudioDicerGUI_wSliders extends JFrame {
			
	private static final long serialVersionUID = 1L;
	private DemoPlayer_adv demoPlayer_adv;
	private AudioDicer audioDicer;
	
	private int initialSliceSize = 44100;
	private int initialOverlapSize = 2000;
	private double initialVolume = 0.5;
	private double initialSpeed = 1;
	private CrossFadeMode crossFadeMode = CrossFadeMode.NONE;
	
	
	AudioDicerGUI_wSliders() {	
		// Streamer setup
		URL url;
		url = AudioDicerExampleGUI.class.getResource("brook.wav");
//		url = AudioDicerExampleGUI.class.getResource("Brownian_6_200.wav");
//		url = AudioDicerExampleGUI.class.getResource("chordgliss.wav");
			audioDicer = new AudioDicer();
		try {
			audioDicer.loadAudioDataFromWAV(url);  
			audioDicer.setSliceSize(initialSliceSize);
			audioDicer.setOverlap(initialOverlapSize);
			audioDicer.setCrossFadeMode(crossFadeMode);
			audioDicer.setVolume(initialVolume);
			audioDicer.setSpeed(initialSpeed);
		} catch (UnsupportedAudioFileException | IOException e1) {
			e1.printStackTrace();
		}

		// Player setup
		demoPlayer_adv = new DemoPlayer_adv(audioDicer);
		Thread thread = new Thread(demoPlayer_adv);
		thread.start();		
		
		// GUI setup
		setTitle("Usage: SlicedAudioStreamer_with Sliders");
		JPanel panel = new JPanel();
		panel.setPreferredSize(new Dimension(400, 200));
		JButton button = new JButton("Press to Start Streamer");
		button.addActionListener(new ActionListener() {			
			@Override
			public void actionPerformed(ActionEvent e) {
				if (demoPlayer_adv.getPlaying()) {
					demoPlayer_adv.setPlaying(false);
					button.setText("Press to Start Streamer");
				} else {
					demoPlayer_adv.setPlaying(true);
					button.setText("Press to Stop Streamer");
				}	
			}
		});
		
		JLabel volumeSliderLabel = new JLabel("Volume");
		JSlider volumeSlider = new JSlider();
		volumeSlider.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				demoPlayer_adv.slicedAudioStreamer.setVolume(
						volumeSlider.getValue()/100.0);
			}});
		
		JLabel speedSliderLabel = new JLabel("Speed");
		JSlider speedSlider = new JSlider();
		speedSlider.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				double val = speedSlider.getValue();
				val = val / 25; // 0:4
				val = Math.pow(2, val); // 1:16
				val = val / 4; // 0.25:4
				demoPlayer_adv.slicedAudioStreamer.setSpeed(val);
				
			}});
		
		JLabel sliceSizeSliderLabel = new JLabel("Slice Size");
		JTextField sliceSizeField = new JTextField();
		sliceSizeField.setText(String.valueOf(initialSliceSize));
		sliceSizeField.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				int sliceSizeFrames = Integer.valueOf(sliceSizeField.getText());
				try {
					audioDicer.setSliceSize(sliceSizeFrames);
				} catch (IllegalArgumentException arg0) {
					sliceSizeField.setText(String.valueOf(
							audioDicer.getSliceSize()));	
				}
			}});

		JLabel overlapSizeSliderLabel = new JLabel("Overlap Size");
		JTextField overlapSizeField = new JTextField();
		overlapSizeField.setText(String.valueOf(initialOverlapSize));
		overlapSizeField.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				int overlapFrames = Integer.valueOf(overlapSizeField.getText());
				try {
					audioDicer.setOverlap(overlapFrames);
				} catch (IllegalArgumentException arg0) {
					overlapSizeField.setText(
							String.valueOf(audioDicer.getOverlap()));
					arg0.printStackTrace();
				}
			}});
		
		JRadioButton rbCrossFadeNone = new JRadioButton(CrossFadeMode.NONE.name());
		rbCrossFadeNone.setSelected(true);
		rbCrossFadeNone.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				audioDicer.setCrossFadeMode(CrossFadeMode.NONE);		
			}});
		
		JRadioButton rbCrossFadeLinear = new JRadioButton(CrossFadeMode.LINEAR.name());
		rbCrossFadeLinear.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				audioDicer.setCrossFadeMode(CrossFadeMode.LINEAR);		
			}});
		
		JRadioButton rbCrossFadeSine = new JRadioButton(CrossFadeMode.SINE.name());
		rbCrossFadeSine.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				audioDicer.setCrossFadeMode(CrossFadeMode.SINE);		
			}});
	
		ButtonGroup bgCrossFade = new ButtonGroup();
		bgCrossFade.add(rbCrossFadeNone);
		bgCrossFade.add(rbCrossFadeLinear);
		bgCrossFade.add(rbCrossFadeSine);
				
		panel.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.PAGE_START;			
		c.fill = GridBagConstraints.BOTH;
		c.ipady = 10;

		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 3;
		panel.add(button, c);
		
		c.gridy = 1;
		c.gridwidth = 1;
		panel.add(volumeSliderLabel, c);
		
		c.gridx = 0;
		c.gridx = 1;
		c.gridwidth = 2;
		panel.add(volumeSlider, c);

		c.gridx = 0;
		c.gridy = 2;
		c.gridwidth = 1;
		panel.add(speedSliderLabel, c);
		
		c.gridx = 1;
		c.gridwidth = 2;
		panel.add(speedSlider, c);

		c.gridx = 0;
		c.gridy = 3;
		c.gridwidth = 1;
		panel.add(sliceSizeSliderLabel, c);
		
		c.gridx = 1;
		c.gridwidth = 1;
		panel.add(sliceSizeField, c);
		
		c.gridx = 0;
		c.gridy = 4;
		c.gridwidth = 1;
		panel.add(overlapSizeSliderLabel, c);
		
		c.gridx = 1;
		c.gridwidth = 1;
		panel.add(overlapSizeField, c);
		
		c.gridx = 0;
		c.gridy = 5;
		c.gridwidth = 1;
		panel.add(rbCrossFadeNone, c);
		
		c.gridx = 1;
		panel.add(rbCrossFadeLinear, c);
		
		c.gridx = 2;
		panel.add(rbCrossFadeSine, c);
		
		add(panel);
		pack();
		setLocationRelativeTo(null);		
	}
}

class DemoPlayer_adv implements Runnable {

	SourceDataLine sdl;
	AudioDicer slicedAudioStreamer;
	
	float[] pcmBuffer;
	byte[] byteBuffer, silence;
	final int PCM_BUFFER_SIZE = 1024 * 4;
	final int BYTE_BUFFER_SIZE = PCM_BUFFER_SIZE * 4; // 4 bytes per frame
	final int SDL_BUFFER_SIZE = 1024 * 8;
	
	private volatile boolean playing;
	public void setPlaying(boolean playCommand) {
		if (playCommand) {
			if (! slicedAudioStreamer.getRunning()) {
				slicedAudioStreamer.start();
			}
		}
		playing = playCommand;
	}
	public boolean getPlaying() { return playing; }
	
	public DemoPlayer_adv(AudioDicer slicedAudioStreamer) {
		this.slicedAudioStreamer = slicedAudioStreamer;
		try {
			sdl = getSourceDataLine();
			sdl.start();
		} catch (LineUnavailableException e) {
			e.printStackTrace();
		}
		pcmBuffer = new float[PCM_BUFFER_SIZE];
		byteBuffer = new byte[BYTE_BUFFER_SIZE];
		silence = new byte[BYTE_BUFFER_SIZE];
	}
	
	@Override
	public void run() 
	{
		while(true)
		{
			if (playing) 
			{
				int n = slicedAudioStreamer.read(byteBuffer);
				sdl.write(byteBuffer, 0, n);
			}
			else
			{	
				sdl.write(silence, 0, BYTE_BUFFER_SIZE);
			}
		}
	}

	//////////// UTILITIES ////////////
	private SourceDataLine getSourceDataLine() throws LineUnavailableException
	{
		AudioFormat audioFmt = new AudioFormat(
					Encoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false);
		Info info = new DataLine.Info(SourceDataLine.class,	audioFmt);
		SourceDataLine sdl = (SourceDataLine)AudioSystem.getLine(info);
		sdl.open(audioFmt, SDL_BUFFER_SIZE);
		return sdl;
	}
}
