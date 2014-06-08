package org.hedera.mapreduce;

import java.io.IOException;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.solr.hadoop.SolrInputDocumentWritable;
import org.apache.solr.hadoop.SolrMapper;
import org.hedera.io.Revision;

import tuan.hadoop.conf.JobConfig;

/**
 * This job reads the Wikipedia revision and feeds into the Solr index
 * 
 * @author tuan
 * 
 */
public class WikiRevisionSolrIndexer extends JobConfig {
	
	private static final class IndexMapper extends SolrMapper<LongWritable, Revision> {

		private Text keyOut = new Text();
		private SolrInputDocumentWritable valueOut;
		
		@Override
		protected void map(LongWritable key, Revision value,
				Context context) throws IOException, InterruptedException {		
			
		}
	}
}
