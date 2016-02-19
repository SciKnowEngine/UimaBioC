package edu.isi.bmkeg.uimaBioC.utils;

import java.util.ArrayList;
import java.util.List;

import org.apache.uima.cas.CAS;
import org.apache.uima.collection.EntityProcessStatus;
import org.apache.uima.collection.StatusCallbackListener;

public class StatusCallbackListenerImpl
		implements StatusCallbackListener
	{

		private final List<Exception> exceptions = new ArrayList<Exception>();
		private boolean isProcessing = true;
	
		public void entityProcessComplete(CAS arg0, EntityProcessStatus arg1)
		{
			if (arg1.isException()) {
				for (Exception e : arg1.getExceptions()) {
					exceptions.add(e);
				}
			}
		}
	
		public void aborted()
		{
			synchronized (this) {
				if (isProcessing) {
					isProcessing = false;
					notify();
				}
			}
		}
	
		public void batchProcessComplete()
		{
			// Do nothing
		}
	
		public void collectionProcessComplete()
		{
			synchronized (this) {
				if (isProcessing) {
					isProcessing = false;
					notify();
				}
			}
		}
	
		public void initializationComplete()
		{
			// Do nothing
		}
	
		public void paused()
		{
			// Do nothing
		}
	
		public void resumed()
		{
			// Do nothing
		}
	}