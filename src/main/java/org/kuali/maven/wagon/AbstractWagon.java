/*
 * Copyright 2004-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.kuali.maven.wagon;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.events.SessionListener;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.observers.Debug;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.proxy.ProxyInfoProvider;
import org.apache.maven.wagon.repository.Repository;
import org.apache.maven.wagon.resource.Resource;

/**
 * An abstract implementation of the Wagon interface. This implementation manages listener and other common behaviors.
 *
 * @author Ben Hale
 * @author Jeff Caddel - Updates for version 2.0 of the Wagon interface
 * @since 1.1
 */
public abstract class AbstractWagon implements Wagon {

	private int timeout;

	private boolean interactive;

	private Repository repository;

	private final boolean supportsDirectoryCopy;

	private final SessionListenerSupport sessionListeners = new SessionListenerSupport(this);

	private final TransferListenerSupport transferListeners = new TransferListenerSupport(this);

	protected AbstractWagon(final boolean supportsDirectoryCopy) {
		this.supportsDirectoryCopy = supportsDirectoryCopy;
	}

	public final void addSessionListener(final SessionListener listener) {
		if (listener.getClass().equals(Debug.class)) {
			// This is a junky listener that spews things to System.out in an ugly way
			return;
		}
		sessionListeners.addListener(listener);
	}

	protected final SessionListenerSupport getSessionListeners() {
		return sessionListeners;
	}

	public final boolean hasSessionListener(final SessionListener listener) {
		return sessionListeners.hasListener(listener);
	}

	public final void removeSessionListener(final SessionListener listener) {
		sessionListeners.removeListener(listener);
	}

	public final void addTransferListener(final TransferListener listener) {
		transferListeners.addListener(listener);
	}

	protected final TransferListenerSupport getTransferListeners() {
		return transferListeners;
	}

	public final boolean hasTransferListener(final TransferListener listener) {
		return transferListeners.hasListener(listener);
	}

	public final void removeTransferListener(final TransferListener listener) {
		transferListeners.removeListener(listener);
	}

	public final Repository getRepository() {
		return repository;
	}

	public final boolean isInteractive() {
		return interactive;
	}

	public final void setInteractive(final boolean interactive) {
		this.interactive = interactive;
	}

	public final void connect(final Repository source) throws ConnectionException, AuthenticationException {
		doConnect(source, null, null);
	}

	public final void connect(final Repository source, final ProxyInfo proxyInfo) throws ConnectionException,
			AuthenticationException {
		connect(source, null, proxyInfo);
	}

	public final void connect(final Repository source, final AuthenticationInfo authenticationInfo)
			throws ConnectionException, AuthenticationException {
		doConnect(source, authenticationInfo, null);
	}

	protected void doConnect(final Repository source, final AuthenticationInfo authenticationInfo,
			final ProxyInfo proxyInfo) throws ConnectionException, AuthenticationException {
		repository = source;
		sessionListeners.fireSessionOpening();
		try {
			connectToRepository(source, authenticationInfo, proxyInfo);
		} catch (ConnectionException e) {
			sessionListeners.fireSessionConnectionRefused();
			throw e;
		} catch (AuthenticationException e) {
			sessionListeners.fireSessionConnectionRefused();
			throw e;
		} catch (Exception e) {
			sessionListeners.fireSessionConnectionRefused();
			throw new ConnectionException("Could not connect to repository", e);
		}
		sessionListeners.fireSessionLoggedIn();
		sessionListeners.fireSessionOpened();
	}

	public final void connect(final Repository source, final AuthenticationInfo authenticationInfo,
			final ProxyInfo proxyInfo) throws ConnectionException, AuthenticationException {
		doConnect(source, authenticationInfo, proxyInfo);
	}

	public final void disconnect() throws ConnectionException {
		sessionListeners.fireSessionDisconnecting();
		try {
			disconnectFromRepository();
		} catch (ConnectionException e) {
			sessionListeners.fireSessionConnectionRefused();
			throw e;
		} catch (Exception e) {
			sessionListeners.fireSessionConnectionRefused();
			throw new ConnectionException("Could not disconnect from repository", e);
		}
		sessionListeners.fireSessionLoggedOff();
		sessionListeners.fireSessionDisconnected();
	}

	public final void get(final String resourceName, final File destination) throws TransferFailedException,
			ResourceDoesNotExistException, AuthorizationException {
		Resource resource = new Resource(resourceName);
		transferListeners.fireTransferInitiated(resource, TransferEvent.REQUEST_GET);
		transferListeners.fireTransferStarted(resource, TransferEvent.REQUEST_GET);

		try {
			getResource(resourceName, destination, new TransferProgress(resource, TransferEvent.REQUEST_GET,
					transferListeners));
			transferListeners.fireTransferCompleted(resource, TransferEvent.REQUEST_GET);
		} catch (TransferFailedException e) {
			throw e;
		} catch (ResourceDoesNotExistException e) {
			throw e;
		} catch (AuthorizationException e) {
			throw e;
		} catch (Exception e) {
			transferListeners.fireTransferError(resource, TransferEvent.REQUEST_GET, e);
			throw new TransferFailedException("Transfer of resource " + destination + "failed", e);
		}
	}

	public final List<String> getFileList(final String destinationDirectory) throws TransferFailedException,
			ResourceDoesNotExistException, AuthorizationException {
		try {
			return listDirectory(destinationDirectory);
		} catch (TransferFailedException e) {
			throw e;
		} catch (ResourceDoesNotExistException e) {
			throw e;
		} catch (AuthorizationException e) {
			throw e;
		} catch (Exception e) {
			sessionListeners.fireSessionError(e);
			throw new TransferFailedException("Listing of directory " + destinationDirectory + "failed", e);
		}
	}

	public final boolean getIfNewer(final String resourceName, final File destination, final long timestamp)
			throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
		Resource resource = new Resource(resourceName);
		try {
			if (isRemoteResourceNewer(resourceName, timestamp)) {
				get(resourceName, destination);
				return true;
			} else {
				return false;
			}
		} catch (TransferFailedException e) {
			throw e;
		} catch (ResourceDoesNotExistException e) {
			throw e;
		} catch (AuthorizationException e) {
			throw e;
		} catch (Exception e) {
			transferListeners.fireTransferError(resource, TransferEvent.REQUEST_GET, e);
			throw new TransferFailedException("Transfer of resource " + destination + "failed", e);
		}
	}

	public final void openConnection() throws ConnectionException, AuthenticationException {
		// Nothing to do here (never called by the wagon manager)
	}

	protected PutContext getPutContext(File source, String destination) {
		Resource resource = new Resource(destination);
		PutContext context = new PutContext();
		context.setResource(resource);
		context.setProgress(new TransferProgress(resource, TransferEvent.REQUEST_PUT, transferListeners));
		context.setListeners(transferListeners);
		context.setDestination(destination);
		context.setSource(source);
		return context;
	}

	public final void put(final File source, final String destination) throws TransferFailedException,
			ResourceDoesNotExistException, AuthorizationException {
		PutContext context = getPutContext(source, destination);

		try {
			context.fireStart();
			putResource(source, destination, context.getProgress());
			context.fireComplete();
		} catch (Exception e) {
			handleException(e, context);
		}
	}

	protected void handleException(Exception e, PutContext context) throws TransferFailedException,
			ResourceDoesNotExistException, AuthorizationException {
		if (e instanceof TransferFailedException) {
			throw (TransferFailedException) e;
		}
		if (e instanceof ResourceDoesNotExistException) {
			throw (ResourceDoesNotExistException) e;
		}
		if (e instanceof AuthorizationException) {
			throw (AuthorizationException) e;
		}
		transferListeners.fireTransferError(context.getResource(), TransferEvent.REQUEST_PUT, e);
		throw new TransferFailedException("Transfer of resource " + context.getDestination() + "failed", e);
	}

	protected List<PutContext> getPutContexts(File sourceDirectory, String destinationDirectory) {
		List<PutContext> contexts = new ArrayList<PutContext>();
		// Cycle through all the files in this directory
		for (File f : sourceDirectory.listFiles()) {

			/**
			 * The filename is used 2 ways:<br>
			 *
			 * 1 - as a "key" into the bucket<br>
			 * 2 - In the http url itself<br>
			 *
			 * We encode it here so the key matches the url AND to guarantee that the url is valid even in cases where
			 * filenames contain characters (eg spaces) that are not allowed in urls
			 */
			String filename = encodeUTF8(f.getName());

			// We hit a directory
			if (f.isDirectory()) {
				// Recurse into the sub-directory and create put requests for any files we find
				contexts.addAll(getPutContexts(f, destinationDirectory + "/" + filename));
			} else {
				PutContext context = getPutContext(f, destinationDirectory + "/" + filename);
				contexts.add(context);
			}
		}
		return contexts;
	}

	protected String encodeUTF8(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	public final boolean resourceExists(final String resourceName) throws TransferFailedException,
			AuthorizationException {
		try {
			return doesRemoteResourceExist(resourceName);
		} catch (TransferFailedException e) {
			throw e;
		} catch (AuthorizationException e) {
			throw e;
		} catch (Exception e) {
			sessionListeners.fireSessionError(e);
			throw new TransferFailedException("Listing of resource " + resourceName + "failed", e);
		}
	}

	public final boolean supportsDirectoryCopy() {
		return supportsDirectoryCopy;
	}

	/**
	 * Subclass must implement with specific connection behavior
	 *
	 * @param source
	 *            The repository connection information
	 * @param authenticationInfo
	 *            Authentication information, if any
	 * @param proxyInfo
	 *            Proxy information, if any
	 * @throws Exception
	 *             Implementations can throw any exception and it will be handled by the base class
	 */
	protected abstract void connectToRepository(Repository source, AuthenticationInfo authenticationInfo,
			ProxyInfo proxyInfo) throws Exception;

	/**
	 * Subclass must implement with specific detection behavior
	 *
	 * @param resourceName
	 *            The remote resource to detect
	 * @return true if the remote resource exists
	 * @throws Exception
	 *             Implementations can throw any exception and it will be handled by the base class
	 */
	protected abstract boolean doesRemoteResourceExist(String resourceName) throws Exception;

	/**
	 * Subclasses must implement with specific disconnection behavior
	 *
	 * @throws Exception
	 *             Implementations can throw any exception and it will be handled by the base class
	 */
	protected abstract void disconnectFromRepository() throws Exception;

	/**
	 * Subclass must implement with specific get behavior
	 *
	 * @param resourceName
	 *            The name of the remote resource to read
	 * @param destination
	 *            The local file to write to
	 * @param progress
	 *            A progress notifier for the upload. It must be used or hashes will not be calculated correctly
	 * @throws Exception
	 *             Implementations can throw any exception and it will be handled by the base class
	 */
	protected abstract void getResource(String resourceName, File destination, TransferProgress progress)
			throws Exception;

	/**
	 * Subclass must implement with newer detection behavior
	 *
	 * @param resourceName
	 *            The name of the resource being compared
	 * @param timestamp
	 *            The timestamp to compare against
	 * @return true if the current version of the resource is newer than the timestamp
	 * @throws Exception
	 *             Implementations can throw any exception and it will be handled by the base class
	 */
	protected abstract boolean isRemoteResourceNewer(String resourceName, long timestamp) throws Exception;

	/**
	 * Subclass must implement with specific directory listing behavior
	 *
	 * @param directory
	 *            The directory to list files in
	 * @return A collection of file names
	 * @throws Exception
	 *             Implementations can throw any exception and it will be handled by the base class
	 */
	protected abstract List<String> listDirectory(String directory) throws Exception;

	/**
	 * Subclasses must implement with specific put behavior
	 *
	 * @param source
	 *            The local source file to read from
	 * @param destination
	 *            The name of the remote resource to write to
	 * @param progress
	 *            A progress notifier for the upload. It must be used or hashes will not be calculated correctly
	 * @throws Exception
	 *             Implementations can throw any exception and it will be handled by the base class
	 */
	protected abstract void putResource(File source, String destination, TransferProgress progress) throws Exception;

	public void connect(final Repository source, final AuthenticationInfo authenticationInfo,
			final ProxyInfoProvider proxyInfoProvider) throws ConnectionException, AuthenticationException {
		doConnect(source, authenticationInfo, null);
	}

	public void connect(final Repository source, final ProxyInfoProvider proxyInfoProvider) throws ConnectionException,
			AuthenticationException {
		doConnect(source, null, null);
	}

	public int getTimeout() {
		return this.timeout;
	}

	public void setTimeout(final int timeoutValue) {
		this.timeout = timeoutValue;
	}

}