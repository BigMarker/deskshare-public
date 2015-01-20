package com.bigmarker.util;

import java.util.concurrent.atomic.AtomicInteger;

public class SequenceNumberGenerator {

	private final AtomicInteger sequenceNum;
	
	public SequenceNumberGenerator() {
		sequenceNum = new AtomicInteger(0);
	}
	
	public int getNext() {
		return sequenceNum.incrementAndGet();
	}
	
	public void reset() {
		sequenceNum.set(0);
	}
	
}
