/*
 * Copyright (c) 2002-2008 LWJGL Project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'LWJGL' nor the names of
 *   its contributors may be used to endorse or promote products derived
 *   from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.lwjgl.opengl;

import java.nio.ByteBuffer;

import org.lwjgl.LWJGLException;
import org.lwjgl.LWJGLUtil;
import org.lwjgl.Sys;

/**
 * <p/>
 * Context encapsulates an OpenGL context.
 * <p/>
 *
 * This class is thread-safe.
 *
 * @author elias_naur <elias_naur@users.sourceforge.net>
 * @version $Revision$
 * $Id$
 */
final class Context {
	/**
	 * The platform specific implementation of context methods
	 */
	private final static ContextImplementation implementation;

	/** The current Context */
	private final static ThreadLocal current_context_local = new ThreadLocal();
	
	/**
	 * Handle to the native GL rendering context
	 */
	private final ByteBuffer handle;
	private final PeerInfo peer_info;

	/** Whether the context has been destroyed */
	private boolean destroyed;

	private boolean destroy_requested;
	
	/** The thread that has this context current, or null. */
	private Thread thread;

	static {
		Sys.initialize();
		implementation = createImplementation();
	}

	private static ContextImplementation createImplementation() {
		switch (LWJGLUtil.getPlatform()) {
			case LWJGLUtil.PLATFORM_LINUX:
				return new LinuxContextImplementation();
			case LWJGLUtil.PLATFORM_WINDOWS:
				return new WindowsContextImplementation();
			case LWJGLUtil.PLATFORM_MACOSX:
				return new MacOSXContextImplementation();
			default:
				throw new IllegalStateException("Unsupported platform");
		}
	}

	PeerInfo getPeerInfo() {
		return peer_info;
	}

	static Context getCurrentContext() {
		return (Context)current_context_local.get();
	}

	/**
	 * Create a context with the specified peer info and shared context
	 */
	public Context(PeerInfo peer_info, Context shared_context) throws LWJGLException {
		Context context_lock = shared_context != null ? shared_context : this;
		// If shared_context is not null, synchronize on it to make sure it is not deleted 
		// while this context is created. Otherwise, simply synchronize on ourself to avoid NPE
		synchronized (context_lock) {
			if (shared_context != null && shared_context.destroyed)
				throw new IllegalArgumentException("Shared context is destroyed");
			GLContext.loadOpenGLLibrary();
			try {
				this.peer_info = peer_info;
				this.handle = implementation.create(peer_info, shared_context != null ? shared_context.handle : null);
			} catch (LWJGLException e) {
				GLContext.unloadOpenGLLibrary();
				throw e;
			}
		}
	}

	/**
	 * Release the current context (if any). After this call, no context is current.
	 */
	public static void releaseCurrentContext() throws LWJGLException {
		Context current_context = getCurrentContext();
		if (current_context != null) {
			implementation.releaseCurrentContext();
			GLContext.useContext(null);
			current_context_local.set(null);
			synchronized (current_context) {
				current_context.thread = null;
				current_context.checkDestroy();
			}
		}
	}

	/**
	 * Release the context from its drawable. This is necessary on some platforms,
	 * like Mac OS X, where binding the context to a drawable and binding the context
	 * for rendering are two distinct actions and where calling releaseDrawable
	 * on every releaseCurrentContext results in artifacts.
	 */
	public synchronized void releaseDrawable() throws LWJGLException {
		if (destroyed)
			throw new IllegalStateException("Context is destroyed");
		implementation.releaseDrawable(getHandle());
	}

	/**
	 * Update the context. Should be called whenever it's drawable is moved or resized
	 */
	public synchronized void update() {
		if (destroyed)
			throw new IllegalStateException("Context is destroyed");
		implementation.update(getHandle());
	}
	
	/**
	 * Swap the buffers on the current context. Only valid for double-buffered contexts
	 */
	public static void swapBuffers() throws LWJGLException {
		implementation.swapBuffers();
	}
	
	private boolean canAccess() {
		return thread == null || Thread.currentThread() == thread;
	}

	private void checkAccess() {
		if (!canAccess())
			throw new IllegalStateException("From thread " + Thread.currentThread() + ": " + thread + " already has the context current");
	}

	/**
	 * Make the context current
	 */
	public synchronized void makeCurrent() throws LWJGLException {
		checkAccess();
		if (destroyed)
			throw new IllegalStateException("Context is destroyed");
		thread = Thread.currentThread();
		current_context_local.set(this);
		implementation.makeCurrent(peer_info, handle);
		GLContext.useContext(this);
	}

	ByteBuffer getHandle() {
		return handle;
	}

	/**
	 * Query whether the context is current
	 */
	public synchronized boolean isCurrent() throws LWJGLException {
		if (destroyed)
			throw new IllegalStateException("Context is destroyed");
		return implementation.isCurrent(handle);
	}

	private void checkDestroy() {
		if (!destroyed && destroy_requested) {
			try {
				releaseDrawable();
				implementation.destroy(peer_info, handle);
				destroyed = true;
				thread = null;
				GLContext.unloadOpenGLLibrary();
			} catch (LWJGLException e) {
				LWJGLUtil.log("Exception occurred while destroying context: " + e);
			}
		}
	}

	/**
	 * Set the buffer swap interval. This call is a best-attempt at changing
	 * the monitor swap interval, which is the minimum periodicity of color buffer swaps,
	 * measured in video frame periods, and is not guaranteed to be successful.
	 *
	 * A video frame period is the time required to display a full frame of video data.
	 *
	 * @param sync true to synchronize; false to ignore synchronization
	 */
	public static void setSwapInterval(int value) {
		implementation.setSwapInterval(value);
	}
	
	
	/**
	 * Destroy the context. This method behaves the same as destroy() with the extra
	 * requirement that the context must be either current to the current thread or not
	 * current at all.
	 */
	public synchronized void forceDestroy() throws LWJGLException {
		checkAccess();
		destroy();
	}
	
	/**
	 * Request destruction of the Context. If the context is current, no context will be current after this call.
	 * The context is destroyed when no thread has it current.
	 */
	public synchronized void destroy() throws LWJGLException {
		if (destroyed)
			return;
		destroy_requested = true;
		boolean was_current = isCurrent();
		int error = GL11.GL_NO_ERROR;
		if (was_current) {
			if (GLContext.getCapabilities() != null && GLContext.getCapabilities().OpenGL11)
				error = GL11.glGetError();
			releaseCurrentContext();
		}
		checkDestroy();
		if (was_current && error != GL11.GL_NO_ERROR)
			throw new OpenGLException(error);
	}
}
