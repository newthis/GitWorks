package gitworks;


import java.util.ArrayList;
import java.util.HashMap;


public class Motif implements Comparable<Object> {

String name;

ArrayList<MetaEdge> allEdges;
ArrayList<MotifOccurrence> occurrences;
ArrayList<Double> cStats; // min mean med max stdev
int minLayer;
int maxLayer;
long minTimestamp;
long maxTimestamp;
int numNodes;
int numEdges;
double zScore;


public Motif(String n, int nodes, int edges) {
  name = n;
  numNodes = nodes;
  numEdges = edges;
  occurrences = new ArrayList<MotifOccurrence>();
  allEdges = new ArrayList<MetaEdge>();
  cStats = new ArrayList<Double>();
  minLayer = Integer.MAX_VALUE;
  maxLayer = 0;
  minTimestamp = Long.MAX_VALUE;
  maxTimestamp = 0L;
  zScore = 0;
}


void addOccurrence(MotifOccurrence mo) {
  minLayer = Math.min(minLayer, mo.minLayer);
  maxLayer = Math.max(maxLayer, mo.maxLayer);
  minTimestamp = Math.min(minTimestamp, mo.minTimestamp);
  maxTimestamp = Math.max(maxTimestamp, mo.maxTimestamp);
  GitWorks.addUnique(occurrences, mo);
  for (MetaEdge me : mo.mEdges)
    GitWorks.addUnique(allEdges, me);
  if (mo.hasParallels()) for (MetaEdge[] mes : mo.parallels.values())
    for (MetaEdge me : mes)
      GitWorks.addUnique(allEdges, me);
}


void addOccurrence(ArrayList<MetaEdge> edges, HashMap<String, ArrayList<MetaEdge>> twins) {
  MotifOccurrence mo = new MotifOccurrence(edges, twins);
  minLayer = Math.min(minLayer, mo.minLayer);
  maxLayer = Math.max(maxLayer, mo.maxLayer);
  minTimestamp = Math.min(minTimestamp, mo.minTimestamp);
  maxTimestamp = Math.max(maxTimestamp, mo.maxTimestamp);
  GitWorks.addUnique(occurrences, mo);
  for (MetaEdge me : mo.mEdges)
    GitWorks.addUnique(allEdges, me);
  if (mo.hasParallels()) for (MetaEdge[] mes : mo.parallels.values())
    for (MetaEdge me : mes)
      GitWorks.addUnique(allEdges, me);
}


public String toString() {
  String res = name + " : ";
  res += occurrences.size();
  return res;
}


int getNumParallels() {
  int res = 0;
  for (MotifOccurrence mo : occurrences)
    res += mo.numParallels;
  return res;
}


@Override
public int compareTo(Object o) {
  if (o instanceof Motif)
    return name.compareTo(((Motif)o).name);
  else if (o instanceof String)
    return name.compareTo(((String)o));
  else
    return -1;
}

}
