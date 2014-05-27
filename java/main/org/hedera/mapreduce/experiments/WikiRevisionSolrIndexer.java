package org.hedera.mapreduce.experiments;

import java.io.IOException;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper.Context;
import org.apache.solr.hadoop.SolrInputDocumentWritable;
import org.apache.solr.hadoop.SolrMapper;
import org.hedera.io.WikiRevisionWritable;

import tuan.hadoop.conf.JobConfig;

/**
 * This job reads the Wikipedia revision and feeds into the Solr index
 * 
 * @author tuan
 * 
 */
public class WikiRevisionSolrIndexer extends JobConfig {
	
	private static final class IndexMapper extends SolrMapper<LongWritable, WikiRevisionWritable> {

		private Text keyOut = new Text();
		private SolrInputDocumentWritable valueOut;
		
		@Override
		protected void map(LongWritable key, WikiRevisionWritable value,
				Context context) throws IOException, InterruptedException {		
			
		}
	}
}
