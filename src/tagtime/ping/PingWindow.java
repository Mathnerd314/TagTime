/*
 * Copyright 2011 Joseph Cloutier, Daniel Reeves, Bethany Soule
 * 
 * This file is part of TagTime.
 * 
 * TagTime is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * 
 * TagTime is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with TagTime. If not, see <http://www.gnu.org/licenses/>.
 */

package tagtime.ping;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import tagtime.Main;
import tagtime.TagTime;
import tagtime.settings.SettingType;
import tagtime.util.TagCount;

/**
 * The popup window displayed for each ping.
 */
public class PingWindow extends JFrame implements ActionListener {
	private static final long serialVersionUID = 1489384636886031541L;
	
	public final TagTime tagTimeInstance;
	
	private static final String SUBMIT = "Submit";
	private static final String CANCEL = "Cancel";
	
	final TextArea inputText;
	final JList quickTags;
	
	private PingJob ownerJob;
	
	public PingWindow(TagTime tagTimeInstance, PingJob ownerJob, List<TagCount> tagCounts) {
		//create the window
		super("Pinging " + tagTimeInstance.username + " - TagTime");
		
		this.tagTimeInstance = tagTimeInstance;
		
		setIconImage(Main.getIconImage());
		setLocation(tagTimeInstance.settings.getIntValue(SettingType.WINDOW_X),
					tagTimeInstance.settings.getIntValue(SettingType.WINDOW_Y));
		
		//record the job that created this window
		this.ownerJob = ownerJob;
		
		//set up the buttons
		final JButton cancelButton = new JButton(CANCEL);
		cancelButton.addActionListener(this);
		final JButton submitButton = new JButton(SUBMIT);
		submitButton.setActionCommand(SUBMIT);
		submitButton.addActionListener(this);
		
		//convert the given list of TagCount objects to a list of strings
		String[] cachedTags = new String[tagCounts.size()];
		for(int i = cachedTags.length - 1; i >= 0; i--) {
			cachedTags[i] = tagCounts.get(i).getTag();
		}
		
		//set up the list of previously-submitted tags
		quickTags = new JList(cachedTags);
		quickTags.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		quickTags.setVisibleRowCount(tagTimeInstance.settings
					.getIntValue(SettingType.CACHED_TAGS_VISIBLE));
		quickTags.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				addSelectedTag();
			}
		});
		
		//prepare the list to be displayed
		JScrollPane listDisplay = new JScrollPane(quickTags);
		
		//set up the input text field
		inputText = new TextArea("", 0, 0, TextArea.SCROLLBARS_NONE);
		inputText.setMinimumSize(new Dimension(200, 15));
		inputText.setPreferredSize(new Dimension(300, 30));
		inputText.setEditable(true);
		inputText.addTextListener(new TextListener() {
			@Override
			public void textValueChanged(TextEvent e) {
				//the submit button should be enabled if and only if text
				//has been entered
				submitButton.setEnabled(inputText.getText().length() > 0);
			}
		});
		
		//create the heading text
		JLabel text = new JLabel("<html>It's tag time! What are you doing <i>right now</i>?");
		text.setAlignmentX(JLabel.LEADING);
		
		//HACK: for some reason, without these settings, the label will
		//get cut off way too early
		//TODO: Find a more reliable way of getting the formatting right
		text.setMinimumSize(new Dimension(1400, 20));
		text.setPreferredSize(text.getMinimumSize());
		
		//add the buttons to their own pane, from left to right
		JPanel buttonPane = new JPanel();
		buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.LINE_AXIS));
		//"horizontal glue" will push the buttons to the right
		buttonPane.add(Box.createHorizontalGlue());
		buttonPane.add(submitButton);
		buttonPane.add(Box.createRigidArea(new Dimension(8, 0)));
		buttonPane.add(cancelButton);
		
		//add the components, from top to bottom
		JRootPane root = getRootPane();
		root.setLayout(new BoxLayout(root, BoxLayout.PAGE_AXIS));
		root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		
		root.add(text);
		root.add(Box.createRigidArea(new Dimension(0, 5)));
		root.add(listDisplay);
		root.add(Box.createRigidArea(new Dimension(0, 3)));
		root.add(inputText);
		root.add(Box.createRigidArea(new Dimension(0, 5)));
		root.add(buttonPane);
		
		int windowHeight = 0;
		
		for(int i = root.getComponentCount() - 1; i >= 0; i--) {
			Component component = root.getComponent(i);
			windowHeight += component.getPreferredSize().height;
		}
		
		setSize(350, Math.max(300, windowHeight));
		
		//the submit button is selected when the user presses enter, but
		//only if it's enabled, and it doesn't get enabled until the user
		//enters a tag
		submitButton.setEnabled(false);
		root.setDefaultButton(submitButton);
		
		//clean up when closed
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		
		//if the user moves this window, record the new location
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentMoved(ComponentEvent e) {
				PingWindow.this.tagTimeInstance.settings.setValue(SettingType.WINDOW_X, getX());
				PingWindow.this.tagTimeInstance.settings.setValue(SettingType.WINDOW_Y, getY());
			}
		});
	}
	
	@Override
	public void setVisible(boolean b) {
		if(b == isVisible()) {
			return;
		}
		
		if(b) {
			//focus on this window and the input text only if the window
			//is allowed to steal focus
			if(tagTimeInstance.settings.getBooleanValue(SettingType.STEAL_FOCUS)) {
				super.setVisible(true);
				inputText.requestFocus();
			} else {
				//in some environments, setting visible to true causes the
				//window to steal focus
				setFocusableWindowState(false);
				super.setVisible(true);
				setFocusableWindowState(true);
			}
			
			playSound();
		} else {
			super.setVisible(false);
		}
	}
	
	/**
	 * Plays the sound associated with opening a window.
	 */
	private void playSound() {
		File soundFile = new File(Main.getSoundDirectory() + "/"
					+ tagTimeInstance.settings.getStringValue(SettingType.SOUND_TO_PLAY));
		
		if(!soundFile.exists()) {
			return;
		}
		
		Clip soundClip;
		try {
			soundClip = AudioSystem.getClip();
			AudioInputStream inputStream = AudioSystem.
						getAudioInputStream(soundFile);
			soundClip.open(inputStream);
		} catch(UnsupportedAudioFileException e) {
			e.printStackTrace();
			return;
		} catch(IOException e) {
			e.printStackTrace();
			return;
		} catch(LineUnavailableException e) {
			e.printStackTrace();
			return;
		}
		
		soundClip.loop(0);
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		String action = e.getActionCommand();
		
		if(action.equals(SUBMIT)) {
			ownerJob.submit(inputText.getText());
			dispose();
			
			//flush all saved data upon submission (multiple windows
			//may be canceled at once, but there should be a minimum of
			//a few seconds between submissions)
			tagTimeInstance.settings.flush();
		} else if(action.equals(CANCEL)) {
			dispose();
		}
	}
	
	@Override
	public void dispose() {
		for(WindowListener listener : getWindowListeners()) {
			removeWindowListener(listener);
		}
		
		//this will do nothing if the data was submitted normally
		ownerJob.submitCanceled();
		
		super.dispose();
	}
	
	void addSelectedTag() {
		Object selectedValue = quickTags.getSelectedValue();
		String currentText = inputText.getText();
		
		//append the selected tag only if it isn't already there
		if(selectedValue != null && !currentText.contains(selectedValue.toString())) {
			//add a space if needed
			if(currentText.length() > 0
						&& currentText.charAt(currentText.length() - 1) != ' ') {
				inputText.append(" ");
			}
			
			inputText.append(selectedValue.toString());
		}
	}
}
