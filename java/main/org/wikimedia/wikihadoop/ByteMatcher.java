/**
 * Copyright 2011 Yusuke Matsubara
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wikimedia.wikihadoop;

import java.io.*;

import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.util.Progressable;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.Seekable;

import com.twitter.elephantbird.util.TaskHeartbeatThread;

public class ByteMatcher {
	private final InputStream in;
	private final Seekable pos;
	private long lastPos;
	private long currentPos;
	private long bytes;
	public ByteMatcher(InputStream in, Seekable pos) throws IOException {
		this.in = in;
		this.pos = pos;
		this.bytes = 0;
		this.lastPos = -1;
		this.currentPos = -1;
	}
	public ByteMatcher(SeekableInputStream is) throws IOException {
		this(is, is);
	}
	public long getReadBytes() {
		return this.bytes;
	}
	public long getPos() throws IOException {
		return this.pos.getPos();
	}
	public long getLastUnmatchPos() { return this.lastPos; }

	public void skip(long len) throws IOException {
		this.in.skip(len);
		this.bytes += len;
	}

	/**
	 * Tuan (22.05.2014) - change the visibility of this method to public for being able to read from other packages
	 */
	public boolean readUntilMatch(String textPat, DataOutputBuffer outBufOrNull, long end,
			final Progressable context) throws IOException {
		byte[] match = textPat.getBytes("UTF-8");
		int i = 0;
		while (true) {
			int b;
			
			// We use a thread that pings back to the cluster every 5 minutes
			// to avoid getting killed for slow read
			if (context != null) {
				TaskHeartbeatThread heartbeat = new TaskHeartbeatThread(context, 60 * 5000) {
					@Override
					protected void progress() {}
				};

				try {
					heartbeat.start();
					b = this.in.read();
				} finally {
					heartbeat.stop();
				}
			} else b = this.in.read();
			

			// end of file:
			if (b == -1) {
				System.err.println("eof 1");
				return false;
			}
			++this.bytes;    //! TODO: count up later in batch
			// save to buffer:
			if (outBufOrNull != null)
				outBufOrNull.write(b);

			// check if we're matching:
			if (b == match[i]) {
				i++;
				if (i >= match.length)
					return true;
			} else {
				i = 0;
				if ( this.currentPos != this.getPos() ) {
					this.lastPos = this.currentPos;
					this.currentPos = this.getPos();
				}
			}
			// see if we've passed the stop point:
			if (i == 0 && this.pos.getPos() >= end) {
				// System.err.println("eof 2: end=" + end);
				return false;
			}
		}
	}
}
