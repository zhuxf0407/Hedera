package org.hedera.pig.load;

import static org.apache.hadoop.mapreduce.lib.input.FileInputFormat.setInputPaths;

import java.io.IOException;
import java.util.Arrays;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.log4j.Logger;
import org.apache.pig.Expression;
import org.apache.pig.LoadFunc;
import org.apache.pig.LoadMetadata;
import org.apache.pig.ResourceSchema;
import org.apache.pig.ResourceStatistics;
import org.apache.pig.backend.hadoop.executionengine.mapReduceLayer.PigSplit;
import org.apache.pig.data.BagFactory;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;
import org.apache.pig.impl.logicalLayer.FrontendException;
import org.hedera.io.RevisionDiff;
import org.hedera.io.input.WikiRevisionDiffInputFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

public class WikiRevisionLoaderTest extends LoadFunc implements LoadMetadata {

	private static final Logger LOG = Logger.getLogger(WikiRevisionLoaderTest.class);
	
	private static final WikiRevisionDiffInputFormat INPUT_FORMAT = new WikiRevisionDiffInputFormat();

	protected RecordReader<LongWritable, RevisionDiff> reader;

	// a cached object that defines the output schema of a Wikipedia page. Use volatile to fix
	// the infamous double-checked locking issue, and to make access to this object thread-safe
	protected volatile ResourceSchema schema;

	// protected diff_match_patch dmp;

	protected TupleFactory tuples;
	protected BagFactory bags;

	/*
	 * Test objects 
	 */
	private static final DateTimeFormatter dtf = ISODateTimeFormat.dateTimeNoMillis();

	@Override
	public InputFormat getInputFormat() throws IOException {
		return INPUT_FORMAT;
	}

	@Override
	public void prepareToRead(@SuppressWarnings("rawtypes") RecordReader reader, PigSplit split)
			throws IOException {
		this.reader = reader;
		// this.dmp = new diff_match_patch();
		this.tuples = TupleFactory.getInstance();
		this.bags = BagFactory.getInstance();
	}

	@Override
	public Tuple getNext() throws IOException {
		try {
			if (reader.nextKeyValue()) {
				LongWritable key = reader.getCurrentKey();
				//Text content = reader.getCurrentValue();
				RevisionDiff r = reader.getCurrentValue();
				String content = r.getPageTitle();
				return tuples.newTupleNoCopy(Arrays.asList(key.get(),content));	
				/*Document doc = Jsoup.parse(content.toString(), "");				
				Elements elems = doc.select("revision");				
				DateTime dt = null;
				for (Element e : elems) {
					Elements subElems = e.getElementsByTag("timestamp");
					if (subElems == null || subElems.isEmpty()) {
						return tuples.newTupleNoCopy(Arrays.asList("1"));
					} else {
						DateTime dt1 = dtf.parseDateTime(subElems.get(0).text());
						if (dt == null) dt = dt1;
						else if (dt1.isAfter(dt)) {
							return tuples.newTupleNoCopy(Arrays.asList("1"));
						} else {
							return tuples.newTupleNoCopy(Arrays.asList("0"));
						}
					}
				}*/
			}
			return null;
		} catch (InterruptedException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void setLocation(String loc, Job job) throws IOException {
		setInputPaths(job, loc);
	}

	@Override
	public String[] getPartitionKeys(String loc, Job job) throws IOException {
		setLocation(loc, job);
		return null;
	}

	@Override
	public ResourceSchema getSchema(String arg0, Job arg1) throws IOException {
		ResourceSchema s = schema;
		if (s == null) {
			synchronized (this) {
				s = schema;
				if (s == null) {					
					defineSchema();
				}
			}
		}
		return schema;
	}

	@Override
	public ResourceStatistics getStatistics(String arg0, Job arg1)
			throws IOException {
		return null;
	}

	@Override
	public void setPartitionFilter(Expression arg0) throws IOException {
	}

	protected void defineSchema() throws FrontendException {
		// TODO: define schema here
	}
}
