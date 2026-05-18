package org.wsp.zen.concurrent.core;

public interface LockedSupplier<T> {
	T execute();
}
