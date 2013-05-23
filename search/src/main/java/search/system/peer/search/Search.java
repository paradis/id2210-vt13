package search.system.peer.search;

import common.configuration.SearchConfiguration;
import cyclon.system.peer.cyclon.CyclonSample;
import cyclon.system.peer.cyclon.CyclonSamplePort;
import cyclon.system.peer.cyclon.PeerDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortField.Type;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timer;
import se.sics.kompics.web.Web;
import se.sics.kompics.web.WebRequest;
import se.sics.kompics.web.WebResponse;
import search.simulator.snapshot.Snapshot;
import search.system.peer.AddIndexText;
import search.system.peer.IndexPort;
import search.system.peer.leader.LeaderElectionNotify;
import search.system.peer.leader.LeaderElectionPort;
import search.system.peer.leader.LeaderRequest;
import search.system.peer.leader.LeaderResponse;
import search.system.peer.search.EntryRequest.Request;
import search.system.peer.search.EntryRequest.Response;
import search.system.peer.search.EntryRequest.Timeout;
import tman.system.peer.tman.TMan;
import tman.system.peer.tman.TManSample;
import tman.system.peer.tman.TManSamplePort;

/**
 * The Search class will handle :
 * the web interface,
 * adding entries (to the correct partition),
 * exchanging entries,
 * being the leader.
 * 
 * However, electing and finding the leader will be done by the LeaderElector component.
 *
 * @author jdowling
 */
public final class Search extends ComponentDefinition {

    private static final Logger logger = LoggerFactory.getLogger(Search.class);
    Positive<IndexPort> indexPort = positive(IndexPort.class);
    Positive<Network> networkPort = positive(Network.class);
    Positive<Timer> timerPort = positive(Timer.class);
    Negative<Web> webPort = negative(Web.class);
    Positive<CyclonSamplePort> cyclonSamplePort = positive(CyclonSamplePort.class);
    Positive<TManSamplePort> tmanPort = positive(TManSamplePort.class);
    Positive<LeaderElectionPort> leaderElectionPort = positive(LeaderElectionPort.class);
    
    ArrayList<Address> neighbours = new ArrayList<Address>();
    List<Address> tmanSample = new ArrayList<Address>();
    private Address self;
    private SearchConfiguration searchConfiguration;
    // Apache Lucene used for searching
    StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_42);
    Directory index = new RAMDirectory();
    IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_42, analyzer);
    int lastMissingIndexEntry = 1;
    int maxIndexEntry = 0;
    Random random;
    
    // TODO : handle multiple simultaneous researches on the same node
    WebRequest currentRequest = null;
    List<SearchResult> currentResearchResults = new ArrayList<SearchResult>();
    
    Queue<String> addingEntryQueue = new LinkedList<String>();
    TreeMap<UUID, String> currentRequests = new TreeMap<UUID, String> ();
    boolean leaderReady = false;
    
    // When you partition the index you need to find new nodes
    // This is a routing table maintaining a list of pairs in each partition.
    private Map<Integer, List<PeerDescriptor>> routingTable;
    
    // Comparator used by the routing system to eject older nodes
    Comparator<PeerDescriptor> peerAgeComparator = new Comparator<PeerDescriptor>() {
        @Override
        public int compare(PeerDescriptor t, PeerDescriptor t1) {
            if (t.getAge() > t1.getAge()) {
                return 1;
            } else {
                return -1;
            }
        }
    };

//-------------------------------------------------------------------	
    public Search() {

        subscribe(handleInit, control);
        subscribe(handleWebRequest, webPort);
        subscribe(handleCyclonSample, cyclonSamplePort);
        subscribe(handleAddIndexText, indexPort);
        subscribe(handleUpdateIndexTimeout, timerPort);
        subscribe(handleMissingIndexEntriesRequest, networkPort);
        subscribe(handleMissingIndexEntriesResponse, networkPort);
        subscribe(handleTManSample, tmanPort);
        subscribe(handleIdRequest, networkPort);
        subscribe(handleIdResponse, networkPort);
        subscribe(handleIdRequestTimeout, timerPort);
        subscribe(handleMaxIdRequest, networkPort);
        subscribe(handleMaxIdResponse, networkPort);
        subscribe(handleMaxIdTimeout, timerPort);
        subscribe(handleLeaderElectionNotify, leaderElectionPort);
        subscribe(handleLeaderResponse, leaderElectionPort);
        subscribe(handleEntryRequest, networkPort);
        subscribe(handleEntryResponse, networkPort);
        subscribe(handleEntryTimeout, timerPort);
    }
//-------------------------------------------------------------------	
    Handler<SearchInit> handleInit = new Handler<SearchInit>() {
        @Override
        public void handle(SearchInit init) {
            self = init.getSelf();
            searchConfiguration = init.getConfiguration();
            routingTable = new HashMap<Integer, List<PeerDescriptor>>(searchConfiguration.getNumPartitions());
            
            for (int partition=0; partition < searchConfiguration.getNumPartitions(); partition++) {
                routingTable.put(partition, new ArrayList<PeerDescriptor>());
            }
            
            random = new Random(init.getConfiguration().getSeed());
            long period = searchConfiguration.getPeriod();
            SchedulePeriodicTimeout rst = new SchedulePeriodicTimeout(period, period);
            rst.setTimeoutEvent(new UpdateIndexTimeout(rst));
            trigger(rst, timerPort);

            // TODO super ugly workaround...
            IndexWriter writer;
            try {
                writer = new IndexWriter(index, config);
                writer.commit();
                writer.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }
    };
    
//-------------------------------------------------------------------------------------------------------------------------------------
//                                              Web interface
//-------------------------------------------------------------------------------------------------------------------------------------
    Handler<WebRequest> handleWebRequest = new Handler<WebRequest>() {
        @Override
        public void handle(WebRequest event) {

            String[] args = event.getTarget().split("-");

            logger.debug("Handling Webpage Request");
            logger.debug(event.getRequest().toString());
            WebResponse response;
            if (args[0].compareToIgnoreCase("search") == 0) {
                currentRequest = event;
                requestEntries(args[1]);
            } else if (args[0].compareToIgnoreCase("add") == 0) {
                response = new WebResponse(addEntryHtml(args[1], Integer.parseInt(args[2])), event, 1, 1);
                trigger(response, webPort);
            } else {
                currentRequest = event;
                requestEntries(event.getTarget());
            }
            
        }
    };

    private String searchPageHtml(List<SearchResult> results) {
        StringBuilder sb = new StringBuilder("<!DOCTYPE html PUBLIC \"-//W3C");
        sb.append("//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR");
        sb.append("/xhtml1/DTD/xhtml1-transitional.dtd\"><html xmlns=\"http:");
        sb.append("//www.w3.org/1999/xhtml\"><head><meta http-equiv=\"Conten");
        sb.append("t-Type\" content=\"text/html; charset=utf-8\" />");
        sb.append("<title>Kompics P2P Bootstrap Server</title>");
        sb.append("<style type=\"text/css\"><!--.style2 {font-family: ");
        sb.append("Arial, Helvetica, sans-serif; color: #0099FF;}--></style>");
        sb.append("</head><body><h2 align=\"center\" class=\"style2\">");
        sb.append("ID2210 (Decentralized Search for Piratebay)</h2><br>");
        
        sb.append("Found ").append(results.size()).append(" entries.<ul>");
        int i = 1;
        for (SearchResult d : results) {
            sb.append("<li>").append(i).append(". ").append(d.getId()).append("\t").append(d.getTitle()).append("</li>");
            i++;
        }
        sb.append("</ul>");

        sb.append("</body></html>");
        return sb.toString();
    }

    private String addEntryHtml(String title, int id) {
        StringBuilder sb = new StringBuilder("<!DOCTYPE html PUBLIC \"-//W3C");
        sb.append("//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR");
        sb.append("/xhtml1/DTD/xhtml1-transitional.dtd\"><html xmlns=\"http:");
        sb.append("//www.w3.org/1999/xhtml\"><head><meta http-equiv=\"Conten");
        sb.append("t-Type\" content=\"text/html; charset=utf-8\" />");
        sb.append("<title>Adding an Entry</title>");
        sb.append("<style type=\"text/css\"><!--.style2 {font-family: ");
        sb.append("Arial, Helvetica, sans-serif; color: #0099FF;}--></style>");
        sb.append("</head><body><h2 align=\"center\" class=\"style2\">");
        sb.append("ID2210 Uploaded Entry</h2><br>");
        try {
            addEntry(title, id);
            sb.append("Entry: ").append(title).append(" - ").append(id);
        } catch (IOException ex) {
            sb.append(ex.getMessage());
            java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private void addEntry(String title, int id) throws IOException {
        if (!isAlreadyKnown(id)) {
            updateIndexPointers(id);
            IndexWriter w = new IndexWriter(index, config);
            Document doc = new Document();
            //TODO: value cannot be null
            doc.add(new TextField("title", title, Field.Store.YES));
            // Use a NumericRangeQuery to find missing index entries:
            // http://lucene.apache.org/core/4_2_0/core/org/apache/lucene/search/NumericRangeQuery.html
            // http://lucene.apache.org/core/4_2_0/core/org/apache/lucene/document/IntField.html
            doc.add(new IntField("id", id, Field.Store.YES));
            w.addDocument(doc);
            w.close();
            Snapshot.incNumIndexEntries(self);
        }
        else {
            logger.debug("Already known value rejected (id: "+id+")");
        }
    }
    
    List<SearchResult> localSearch(String queryStr, int maxHits) throws IOException, ParseException {
        // the "title" arg specifies the default field to use when no field is explicitly specified in the query.
        Query q = new QueryParser(Version.LUCENE_42, "title", analyzer).parse(queryStr);
        IndexSearcher searcher = null;
        IndexReader reader = null;
        try {
            reader = DirectoryReader.open(index);
            searcher = new IndexSearcher(reader);
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(-1);
        }

        TopScoreDocCollector collector = TopScoreDocCollector.create(maxHits, true);
        searcher.search(q, collector);
        ScoreDoc[] hits = collector.topDocs().scoreDocs;
        
        int myPartition = self.getId() % searchConfiguration.getNumPartitions();
        List<SearchResult> result = new ArrayList<SearchResult>();
        for (int i = 0; i < hits.length; ++i) {
            int docId = hits[i].doc;
            result.add(new SearchResult(myPartition, searcher.doc(docId), hits[i].score));
        }
        
        reader.close();
        
        return result;
    }
    
    void requestEntries(String queryString) {
        currentResearchResults.clear();
        logger.debug(self.getId() + " : request entry ["+queryString+"] ; I known "+routingTable.keySet().size()+" partitions.");
        for (List<PeerDescriptor> nodeList : routingTable.values()) {
            List<PeerDescriptor> shortList = nodeList;
            
            // IMPROVEMENT TODO : make that 3 a parameter of the component
            if (shortList.size() > 3) {
                shortList = shortList.subList(0, 3);
            }
            for (PeerDescriptor p : shortList) {
                trigger (new EntryRequest.Request(self, p.getAddress(), queryString), networkPort);
            }
        }
        
        ScheduleTimeout st = new ScheduleTimeout(2000);
        st.setTimeoutEvent(new EntryRequest.Timeout(st));
        trigger(st, timerPort);
    }
    
    Handler<EntryRequest.Request> handleEntryRequest = new Handler<EntryRequest.Request>() {
        @Override
        public void handle(Request e) {
            int myPartition = self.getId() % searchConfiguration.getNumPartitions();
            logger.debug(self.getId() + " : [partition "+myPartition+"] received request for query "+e.getQuery());
            
            try {
                List<SearchResult> localResult = localSearch(e.getQuery(), 10);
                trigger (new EntryRequest.Response(self, e.getSource(), localResult), networkPort);
            } catch (IOException ex) {
                java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
            } catch (ParseException ex) {
                java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    };
    
    Handler<EntryRequest.Response> handleEntryResponse = new Handler<EntryRequest.Response>() {
        @Override
        public void handle(Response e) {
            List<SearchResult> newResults = e.getHits();
            
            
                logger.debug(self.getId()+" : merging " + newResults.size()+" results");
                
            // Merge old and new results.
            // Results are considered equal if they have the same id and partition number.
            for(SearchResult r : newResults) {
                boolean notInList = true;
                for (SearchResult cr : currentResearchResults) {
                    if (r.getId().compareTo(cr.getId()) == 0 && r.getPartition() == cr.getPartition()) {
                        notInList = false;
                    }    
                }
                
                if (notInList) {
                    currentResearchResults.add(r);
                }
            }
            logger.debug(self.getId()+" : currently "+currentResearchResults.size()+" results");
        }
    };
    
    Handler<EntryRequest.Timeout> handleEntryTimeout = new Handler<EntryRequest.Timeout>() {
        @Override
        public void handle(Timeout e) {
            logger.debug(self.getId()+" : research timeout ["+currentResearchResults.size()+" results]");
            Collections.sort(currentResearchResults, new SearchResult.ComparatorByScore());
            
            if (currentResearchResults.size() > 10) {
                currentResearchResults = currentResearchResults.subList(0, 10);
            }
            
            trigger (new WebResponse(searchPageHtml(currentResearchResults), currentRequest, 1, 1), webPort);
            
            currentRequest = null;
            currentResearchResults.clear();
        }
    };
    
//-------------------------------------------------------------------------------------------------------------------------------------
//                                              Periodic index exchanges
//-------------------------------------------------------------------------------------------------------------------------------------
    Handler<UpdateIndexTimeout> handleUpdateIndexTimeout = new Handler<UpdateIndexTimeout>() {
        @Override
        public void handle(UpdateIndexTimeout event) {
            
            ArrayList<Address> allNeighbours = new ArrayList<Address>(tmanSample);
            
            // add some neighbours
            int myPartition = self.getId() % searchConfiguration.getNumPartitions();
         
            List<Address> neigh = new ArrayList<Address>();
            Iterator<PeerDescriptor> it = routingTable.get(myPartition).iterator();
            //TODO : parametrize ratio tman / neighbors
            while (it.hasNext() && neigh.size() < tmanSample.size() * 0.75)
                neigh.add(it.next().getAddress());
            
            allNeighbours.addAll(neigh);
            
            if (allNeighbours.isEmpty())
                return;

            // find all missing index entries (ranges) between lastMissingIndexValue
            // and the maxIndexValue
            List<Range> missingIndexEntries = getMissingRanges();

            // Concurrent requests
            //TODO parametrize number
            for (int i=0; !allNeighbours.isEmpty() && i<2; i++)
            {
                Address dest = allNeighbours.get(random.nextInt(allNeighbours.size()));
                allNeighbours.remove(dest);
                // Send a MissingIndexEntries.Request for the missing index entries to dest
                MissingIndexEntries.Request req = new MissingIndexEntries.Request(self, dest, missingIndexEntries);
                trigger(req, networkPort);
            }
        }
    };

    ScoreDoc[] getExistingDocsInRange(int min, int max, IndexReader reader,
            IndexSearcher searcher) throws IOException {
        reader = DirectoryReader.open(index);
        searcher = new IndexSearcher(reader);
        // The line below is dangerous - we should bound the number of entries returned
        // so that it doesn't consume too much memory.
        int hitsPerPage = max - min > 0 ? max - min : 1;
        Query query = NumericRangeQuery.newIntRange("id", min, max, true, true);
        TopDocs topDocs = searcher.search(query, hitsPerPage, new Sort(new SortField("id", Type.INT)));
        return topDocs.scoreDocs;
    }
    
    // Returns true if this peer already has entry n° id
    boolean isAlreadyKnown(int id) throws IOException {
        IndexReader reader = DirectoryReader.open(index);
        IndexSearcher searcher = new IndexSearcher(reader);

        ScoreDoc[] hits = getExistingDocsInRange(id, id, reader, searcher);
        return hits != null && hits.length > 0;
    }
        
    List<Range> getMissingRanges() {
        List<Range> res = new ArrayList<Range>();
        IndexReader reader = null;
        IndexSearcher searcher = null;
        try {
            reader = DirectoryReader.open(index);
            searcher = new IndexSearcher(reader);
            ScoreDoc[] hits = getExistingDocsInRange(lastMissingIndexEntry, maxIndexEntry, reader, searcher);
            if (hits != null) {
                int startRange = lastMissingIndexEntry;
                for (int i = 0; i < hits.length; i++) {
                    int indexId = idOfScoreDoc(hits[i], searcher);
                    
                    if (indexId == startRange) {
                        startRange++;
                        updateIndexPointers(indexId);
                    }
                    else {
                        res.add (new Range(startRange, indexId-1));
                        startRange = indexId+1;
                    }
                }
                
                // Add all entries > maxIndexEntry as a range of interest.
                res.add(new Range(startRange, Integer.MAX_VALUE));
            }
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ex) {
                    java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

        }


        return res;
    }

    List<IndexEntry> getMissingIndexEntries(Range range) {
        List<IndexEntry> res = new ArrayList<IndexEntry>();
        IndexSearcher searcher = null;
        IndexReader reader = null;
        try {
            reader = DirectoryReader.open(index);
            searcher = new IndexSearcher(reader);
            ScoreDoc[] hits = getExistingDocsInRange(range.getLower(), range.getUpper(), reader, searcher);
            if (hits != null) {
                for (int i = 0; i < hits.length; ++i) {
                    int docId = hits[i].doc;
                    Document d;
                    try {
                        d = searcher.doc(docId);
                        int indexId = Integer.parseInt(d.get("id"));
                        String text = d.get("title");
                        res.add(new IndexEntry(indexId, text));
                    } catch (IOException ex) {
                        java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ex) {
                    java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

        }

        return res;
    }

    /**
     * Called by null null     {@link #handleMissingIndexEntriesRequest(MissingIndexEntries.Request) 
     * handleMissingIndexEntriesRequest}
     *
     * @return List of IndexEntries at this node great than max
     */
    List<IndexEntry> getEntriesGreaterThan(int max) {
        List<IndexEntry> res = new ArrayList<IndexEntry>();

        IndexSearcher searcher = null;
        IndexReader reader = null;
        try {
            ScoreDoc[] hits = getExistingDocsInRange(max, maxIndexEntry, reader, searcher);

            if (hits != null) {
                for (int i = 0; i < hits.length; ++i) {
                    int docId = hits[i].doc;
                    Document d;
                    try {
                        reader = DirectoryReader.open(index);
                        searcher = new IndexSearcher(reader);
                        d = searcher.doc(docId);
                        int indexId = Integer.parseInt(d.get("id"));
                        String text = d.get("text");
                        res.add(new IndexEntry(indexId, text));
                    } catch (IOException ex) {
                        java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ex) {
                    java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

        }
        return res;
    }
    
    Handler<MissingIndexEntries.Request> handleMissingIndexEntriesRequest = new Handler<MissingIndexEntries.Request>() {
        @Override
        public void handle(MissingIndexEntries.Request event) {

            List<IndexEntry> res = new ArrayList<IndexEntry>();
            for (Range r : event.getMissingRanges()) {
                res.addAll(getMissingIndexEntries(r));
            }
            
            trigger(new MissingIndexEntries.Response(self, event.getSource(), res), networkPort);
        }
    };
    
    Handler<MissingIndexEntries.Response> handleMissingIndexEntriesResponse = new Handler<MissingIndexEntries.Response>() {
        @Override
        public void handle(MissingIndexEntries.Response event) {
            if(event.getEntries().size() > 0) {
                logger.debug(self.getId() + " : Merging "+event.getEntries().size() + " data from "+event.getSource().toString());
            }
            
            for(IndexEntry e : event.getEntries()) {
                // TODO : plus joli que ça
                try {
                    logger.debug ("Merging entry " + e.getIndexId());
                    addEntry(e.getText(), e.getIndexId());
                } catch (IOException ex) {
                    java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    };
    
    Handler<CyclonSample> handleCyclonSample = new Handler<CyclonSample>() {
        @Override
        public void handle(CyclonSample event) {
            // receive a new list of neighbours
            neighbours.clear();
            neighbours.addAll(event.getSample());

            // update routing tables
            // TODO : remove duplicates
            for (Address p : neighbours) {
                int partition = p.getId() % searchConfiguration.getNumPartitions();
                List<PeerDescriptor> nodes = routingTable.get(partition);

                TMan.merge(nodes, new PeerDescriptor(p));
                
                // keep the freshest descriptors in this partition
                Collections.sort(nodes, peerAgeComparator);
                while(nodes.size() > searchConfiguration.getMaxNumRoutingEntries())
                    nodes.remove(searchConfiguration.getMaxNumRoutingEntries());
            }
        }
    };
    
    //-------------------------------------------------------------------	
    Handler<AddIndexText> handleAddIndexText = new Handler<AddIndexText>() {
        @Override
        public void handle(AddIndexText event) {
            addingEntryQueue.add(event.getText());
            trigger(new LeaderRequest(), leaderElectionPort);
            logger.debug(self.getId()+" : requested leader for entry "+event.getText());
        }
    };
    
    

    private void updateIndexPointers(int id) {
        if (id == lastMissingIndexEntry) {
            lastMissingIndexEntry++;
        }
        if (id > maxIndexEntry) {
            maxIndexEntry = id;
        }
    }
    
    private int idOfScoreDoc(ScoreDoc hit, IndexSearcher searcher) throws IOException {
        int docId = hit.doc;
        Document d = searcher.doc(docId);
        return Integer.parseInt(d.get("id"));
    }
    
    
//-------------------------------------------------------------------------------------------------------------------------------------
//                                              TMan samples
//-------------------------------------------------------------------------------------------------------------------------------------
    Handler<TManSample> handleTManSample = new Handler<TManSample>() {
        @Override
        public void handle(TManSample event) {
            // Retrieve sample, and forward it to LeaderElection component as well
            tmanSample = event.getSample();
            trigger(event, leaderElectionPort);
        }
    };
    
//-------------------------------------------------------------------------------------------------------------------------------------
//                                              Election and abdication management
//-------------------------------------------------------------------------------------------------------------------------------------
    Handler<LeaderElectionNotify> handleLeaderElectionNotify = new Handler<LeaderElectionNotify>() {
        @Override
        public void handle(LeaderElectionNotify e) {
            // Two cases here :
            // -either getOldLeader() == self, in which case it means we just abdicated
            // -or getOldLeader() != self, in which case we are actually the new leader
            // However, in the second case, we won't consider ourself leader until we
            // received a response from the old leader (or our request timeouted).
            
            leaderReady = false;
            logger.debug(self.getId()+" : received election notification");
            
            if (e.getOldLeader() == self) {
                return;
            }
            
            if (e.getOldLeader() != null) {
                trigger(new MaxIdRequest.Request(self, e.getOldLeader()), networkPort);
            }
            
            ScheduleTimeout st = new ScheduleTimeout(10000);
            st.setTimeoutEvent(new MaxIdRequest.Timeout(st));
            trigger(st, timerPort);
        }
    };
    
    Handler<MaxIdRequest.Request> handleMaxIdRequest = new Handler<MaxIdRequest.Request>() {
        @Override
        public void handle(MaxIdRequest.Request e) {
            trigger(new MaxIdRequest.Response(self, e.getSource(), maxIndexEntry), networkPort);
        }
    };
    
    Handler<MaxIdRequest.Response> handleMaxIdResponse = new Handler<MaxIdRequest.Response>() {
        @Override
        public void handle(MaxIdRequest.Response e) {
            maxIndexEntry = Math.max (e.getResponse(), maxIndexEntry);
            leaderReady = true;
        }
    };
    
    Handler<MaxIdRequest.Timeout> handleMaxIdTimeout = new Handler<MaxIdRequest.Timeout>() {
        @Override
        public void handle(MaxIdRequest.Timeout e) {
            leaderReady = true;
        }
    };
    
//-------------------------------------------------------------------------------------------------------------------------------------
//                                              Finding the current leader
//-------------------------------------------------------------------------------------------------------------------------------------
    Handler<LeaderResponse> handleLeaderResponse = new Handler<LeaderResponse>() {
        @Override
        public void handle(LeaderResponse e) {
            if (e.getLeader() == null) {
                logger.error(self.getId()+" received leader response, but it was empty");
                // Wait for timeout, an then we'll try again
                return;
            }
            
            logger.debug(self.getId()+" received leader response : "+e.getLeader().getId() );
            
            while(!addingEntryQueue.isEmpty()) {
                String entry = addingEntryQueue.poll();
                trigger(new IdRequest.Request(self, e.getLeader(), entry), networkPort);
                logger.debug(self.getId()+" requested id for entry " + entry);
                
                // TODO : correct period
                ScheduleTimeout st = new ScheduleTimeout(2000);
                st.setTimeoutEvent(new IdRequest.Timeout(st));
                
                currentRequests.put(st.getTimeoutEvent().getTimeoutId(), entry);
                trigger(st, timerPort);
            }
        }
    };
    
//-------------------------------------------------------------------------------------------------------------------------------------
//                                              Getting an id from the leader
//-------------------------------------------------------------------------------------------------------------------------------------
    Handler<IdRequest.Request> handleIdRequest = new Handler<IdRequest.Request>() {
        @Override
        public void handle(IdRequest.Request e) {
            // If I'm not able to handle id requests, drop the message
            if (!leaderReady) {
                logger.debug(self.getId()+" : received id request, but I don't feel ready or am not the leader");
                return;
            }
            
            String entry = e.getEntry();
            int entryId = ++maxIndexEntry;
            
            logger.debug(self.getId()+" : received id request for entry: " + entry + ", id: "+ entryId);
            
            try {
                addEntry(entry, entryId);
            } catch (IOException ex) {
                java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            trigger(new IdRequest.Response(self, e.getSource(), entryId, entry), networkPort);
        }
    };
    
    Handler<IdRequest.Response> handleIdResponse = new Handler<IdRequest.Response>() {
        @Override
        public void handle(IdRequest.Response e) {
            
            int idEntry = e.getEntryId();
            String entry = e.getEntry();
            
            logger.debug(self.getId() + " : received id response [" + idEntry + ", " + entry + "]");
            
            UUID timeout = getTimeoutIDFromValue(currentRequests, entry);
            if (timeout == null)
            {
                logger.error(self.getId() + " : Invalid entry: " + entry);
                return;
            }
            
            currentRequests.remove(timeout);
            
            trigger(new CancelTimeout(timeout), timerPort);

            logger.info(self.getId() + " - adding index entry: {} Id={}", entry, idEntry);
            try {
                addEntry(entry, idEntry);
            } catch (IOException ex) {
                java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
                throw new IllegalArgumentException(ex.getMessage());
            }
        }
    };
    
    Handler<IdRequest.Timeout> handleIdRequestTimeout = new Handler<IdRequest.Timeout>() {
        @Override
        public void handle(IdRequest.Timeout e) {
            String entry = currentRequests.remove(e.getTimeoutId());
            
            addingEntryQueue.add(entry);
            trigger(new LeaderRequest(), leaderElectionPort);
        }
    };
    
    UUID getTimeoutIDFromValue(Map<UUID, String> map, String value) {
        for (UUID a : map.keySet()) {
            if (map.get(a).equals(value)) {
                return a;
            }
        }

        return null;
    }
}
