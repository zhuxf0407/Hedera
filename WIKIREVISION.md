Working with Wikipedia Revision History dumps
======

(Disclaimer: This documentation is still in progress and will be incrementally added soon)

Working with Wikipedia in Hedera is fairly easy. To load the datasets in Pig, simply register the library and call its various methods in package <code>org.hedera.pig.eval</code>. A new EvalFunc can also be developed by inheriting the <code>PageFunc</code> abstract class.

###### Example code in Pig######

<pre>
`REGISTER '$LIBDIR/hedera-0.1-SNAPSHOT.jar'
%DECLARE BASEDIR 'hdfs://master.ib:8020'
wiki = LOAD '$BASEDIR/$INPUT' USING org.hedera.pig.load.LiteWikipediaLoader
ewiki = FOREACH wiki GENERATE page_id, org.hedera.pig.ExtractTemplate(page_id,page_title,text);`
</pre>


Transformation
=============

For the moment, Hedera supports the following InputFormat types to transform and split snapshots into Mapper-ready inputs:

1. `WikiRevisionInputTextFormat`: It reads and emits, for every single snapshot of a Wikipedia page, the page header, text of the snapshots in wikimedia markup syntax.

2. `WikiRevisionPageInputFormat`: It reads and emits, for every single snapshot of a Wikipedia page, the WikiRevision object which contains:
   - Ids of page
   - Id of current revision and the immediate preceding ones.
   - Timestamp of the revision
   - Text of the revision content (with or without markups)
   - Namespace
   - Title of revision

3. `WikiRevisionPairInputFormat`: It works the same as `WikiRevisionTextInputFormat`, except that it emits every pair of consecutive snapshots instead on each individually.

4. `WikiRevisionDiffInputFormat`: It reads and compares two revisions of Wikipedia (consecutive or time-sampled, based on configuration), and emits the differentials between the two, each with the following format:
   - Type of differential (INSERT, DELETE, CHANGE)
   - Chunks of original and revised text


###### Example in Java######
Here is the example for registering a job to a specific InputSplit:

<pre>
<code>
  // Use tuan4j-distributed lib for fast configuration of Hadoop jobs
  import tuan.hadoop.conf.JobConfig
  
  ....
  
  public class MyJob extends JobConfig {
    
    
  public void run(String[] args) {
      String inputDir = Wikipedia_revision_files_path;
      String outputDir = output_path;
      int reduceNo = some_number;
      Job job = setup("Job description", YOURJOB.class, inputDir, outputDir,
	      WikiRevisionDiffInputFormat.class, TextOutputFormat.class,
	      MAPPEROUTPUTKEY.class, MAPPEROUTPUTVAL.class,
	      Text.class, Text.class,
	      MyMapper.class, Reducer.class, reduceNo);
	
	  ...
	}
</code>
</pre>



Handling Text:
=============

Hedera supports basic tasks that are pretty much required for almost all text mining work. You can
either work directly with raw dump files in XML format, or with processed JSON ones.


##### 1. Working with Articles#####
Wikipedia revision history dumps includes pages of all types (articles, talks, User pages, discussions, etc. Full list [here](http://en.wikipedia.org/wiki/Wikipedia:Namespace)). In Hedera, you can choose to extract information from article pages only by specifying the configuration variable "org.hedera.input.onlyarticle". In Java you can do it by:

<code>
jobConfigurationObject.setBoolean(WikiRevisionInputFormat.SKIP_NON_ARTICLES, true);
</code>

or in Pig:

<code>
SET 'org.hedera.input.onlyarticle' true
</code>


##### 2. Specify time span#####
The revision history dump contains all text in Wikimedia projects from the beginning (2001) till the date of the dumps. You can specify one particular date of your choice with the following code snippets:

###### Example in Java######
<pre>
<code>
hadoop jar hedera-0.X-SNAPSHOT.jar [CLASS_NAME] -begin [TIME1] -end [TIME2]
</code>
</pre>

###### Example in Pig######
<pre>
<code>
SET 'org.hedera.input.begintime' TIME1
SET 'org.hedera.input.endtime' TIME2
</code>
</pre>

where TIME1 and TIME2's are strings in ISOTime format.


##### 3. Building Dictionary, Vectorization#####

(this is still tentative. To be updated soon)

In Hedera, you can also build basic data structures for Information Retrieval / matrix-like computation, such as mappings of entity / revision / texts into continous ID arrays. 


###### 3.1. Flattening dumps to CSV files ######
(Thanks to contribution from giangbinhtran :) )

One of the favourite format for handling text in different programming languages (e.g. Python) is plain-text CSV files. In Hedera you can flatten the XML dumps into CSV files simply by calling the Pig script XML2JSON.pig in source directory <code>pig/utils</code>. The output is the set of .csv files, each containing one revision per line in JSON format with the following schema:


<pre>
<code>
{
 "page_id":NUMBER,
 "page_title":STRING,
 "page_namespace":NUMBER,
 "rev_id":NUMBER,
 "parent_id":NUMBER,
 "timestamp":NUMBER,
 "user":STRING,
 "user_id":NUMBER,
 "comment":STRING,
 "text":STRING
}
</code>
</pre>

==============
#### Copyright####
Hedera uses the following library for working with Wikipedia

+ [Cloud9](https://github.com/antoine-tran/Cloud9) : Hedera uses the WikipediaPage input formats and Writable implementations from Cloud9 tools
+ [tuan4j-distributed](https://github.com/antoine-tran/maven-repo): Tuan4j-distributed is a set of utilities for handling customized Java objects and setting up jobs in Hadoop environment. It also supports bz2 compression-decompression in reading mapper inputs streams.
+ [pignlproc](https://github.com/ogrisel/pignlproc): PigNLPRoc is an execellent tool for working with Wikipedia article dumps in Pig. Hedera inherits the parsing code from PigNLPRoc and provides the same functionalities in Wikipedia revision dumps.
+ [wikihadoop](https://github.com/whym/wikihadoop): WikiHadoop provides a fast and simple way to work with Wikipedia revision in plain text manner. Unfortunately, it is poorly-documented, no longer active developed (not compatible with Pig 0.11 and CDH4.3.0 and higher), and does not provide fast and approximated reading. Hedera inherits the splitting techniques from Wikihadoop when handling XML files.
