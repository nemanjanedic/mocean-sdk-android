//
// Copyright (C) 2011, 2012 Mocean Mobile. All Rights Reserved. 
//
package com.MASTAdView.core;


import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;


public class AdMessageHandler extends Handler
{
	// Messages sent to and processed by handler
	public static final int	MESSAGE_RESIZE 		= 1000;
	public static final int	MESSAGE_CLOSE 		= 1001;
	public static final int	MESSAGE_HIDE 		= 1002;
	//public static final int	MESSAGE_SHOW 		= 1003;
	public static final int	MESSAGE_EXPAND 		= 1004;
	public static final int	MESSAGE_ANIMATE 	= 1005;
	public static final int	MESSAGE_OPEN 		= 1006;
	public static final int	MESSAGE_PLAY_VIDEO 	= 1007;
	public static final int MESSAGE_CREATE_EVENT = 1008;
	public static final int	MESSAGE_RAISE_ERROR = 1009;

	// public static final int	MESSAGE_PLAY_AUDIO 	= xxxx;
	
	
	// Keys for information passed around in data object
	public static final String ERROR_MESSAGE	= "error.Message";
	public static final String ERROR_ACTION 	= "error.Action";
	public static final String RESIZE_HEIGHT 	= "resize.Height";
	public static final String RESIZE_WIDTH 	= "resize.Width";
	public static final String EXPAND_URL 		= "expand.Url";
	public static final String OPEN_URL 		= "open.Url";
	public static final String PLAYBACK_URL 	= "playback.Url";
		
	
	private AdViewContainer adView;
	private MraidInterface mraidInterface;
	
	
	public AdMessageHandler(AdViewContainer parent)
	{
		super();
		adView = parent;
		mraidInterface = null;
	}
	
	
	// Handle messages asking functions to be performed on the UI thread; primarily used by JavaScript interface
	// so that operations such as open/expand/resize can be performed on the UI thread. Can also be used by other
	// background threads to run code on UI thread when needed.
	@Override
	public void handleMessage(Message msg)
	{
		String error = null;
		Bundle data = msg.getData();
		
		if (mraidInterface == null)
		{
			mraidInterface = adView.getAdWebView().getMraidInterface();
		}
		
		
		switch (msg.what)
		{
			case MESSAGE_RESIZE:
			{
				error = adView.resize(data); 
				if (error != null)
				{
					mraidInterface.fireErrorEvent(error, "resize"); // XXX string 
				}
				break;
			}				
			case MESSAGE_CLOSE:
			{
				error = adView.close(data);
				if (error != null)
				{
					mraidInterface.fireErrorEvent(error, "close"); // XXX string 
				}
				break;
			}
			case MESSAGE_HIDE:
			{
				error = adView.hide(data);
				if (error != null)
				{
					mraidInterface.fireErrorEvent(error, "hide"); // XXX string 
				}
				break;
			}
			/*
			case MESSAGE_SHOW:
			{
				error = adView.show(data);
				if (error != null)
				{
					mraidInterface.fireErrorEvent(error, "show"); // XXX string 
				}
				break;
			}
			*/
			case MESSAGE_EXPAND:
			{
				error = adView.expand(data);
				if (error != null)
				{
					mraidInterface.fireErrorEvent(error, "expand"); // XXX string 
				}
				break;
			}
			case MESSAGE_OPEN:
			{
				error = adView.open(data);
				if (error != null)
				{
					mraidInterface.fireErrorEvent(error, "open"); // XXX string 
				}
				break;
			}
			/*
			case MESSAGE_PLAY_AUDIO:
			{
				error = adView.playAudio(data);
				if (error != null)
				{
					mraidInterface.fireErrorEvent(error, "Mraid.PlayAudio()"); // XXX string 
				}
				break;
			}
			*/
			case MESSAGE_PLAY_VIDEO:
			{
				error = adView.playVideo(data);
				if (error != null)
				{
					mraidInterface.fireErrorEvent(error, "playVideo"); // XXX string 
				}
				break;
			}
			case MESSAGE_CREATE_EVENT:
			{
				error = adView.createCalendarEvent(data);
				if (error != null)
				{
					mraidInterface.fireErrorEvent(error, "createCalendarEvent"); // XXX string 
				}
				break;
			}
			case MESSAGE_RAISE_ERROR:
			{
				String errorMessage = data.getString(ERROR_MESSAGE);
				String action = data.getString(ERROR_ACTION);
				System.out.println("Handler.Error: msg=" + msg + ", action=" + action);
				mraidInterface.fireErrorEvent(errorMessage, action);
				break;
			}
		}
		
		super.handleMessage(msg);
	}
}