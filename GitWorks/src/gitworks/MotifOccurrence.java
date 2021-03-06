package gitworks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;


public class MotifOccurrence implements Comparable<MotifOccurrence> {

Commit[] mNodes; // sorted array of terminal commits
ArrayList<MetaEdge> mEdges; // sorted list of single metaedges (no parallel)
HashMap<MetaEdge, MetaEdge[]> parallels; // map of sorted arrays of parallel edges (including edges in mEdges)
int minLayer;
int maxLayer;
long minTimestamp;
long maxTimestamp;
int totEdges; // total number of edges (parallel included)
int numParallels; // how many groups of parallels
int weight;


// only to make a motif out of parallel edges
MotifOccurrence(ArrayList<MetaEdge> parEdges) {
  mNodes = new Commit[2];
  mNodes[0] = parEdges.get(0).first;
  mNodes[1] = parEdges.get(0).last;
  Arrays.sort(mNodes);
  mEdges = new ArrayList<MetaEdge>(parEdges.size());
  weight = 0;
  for (MetaEdge m : parEdges) {
    GitWorks.addUnique(mEdges, m);
    weight += m.getWeight();
  }
  minLayer = parEdges.get(0).first.layer;
  maxLayer = parEdges.get(0).last.layer;
  minTimestamp = parEdges.get(0).startTimestamp;
  maxTimestamp = parEdges.get(0).endTimestamp;
  totEdges = mEdges.size();
  numParallels = 1;
  parallels = null;
}


MotifOccurrence(ArrayList<MetaEdge> edges, HashMap<String, ArrayList<MetaEdge>> twins) {
  minLayer = Integer.MAX_VALUE;
  maxLayer = Integer.MIN_VALUE;
  minTimestamp = Long.MAX_VALUE;
  maxTimestamp = Long.MIN_VALUE;
  numParallels = 0;
  mEdges = edges;
  ArrayList<MetaEdge> t;
  ArrayList<Commit> nodes = new ArrayList<Commit>();
  parallels = new HashMap<MetaEdge, MetaEdge[]>();
  for (MetaEdge m : mEdges) {
    minLayer = Math.min(minLayer, m.first.layer);
    maxLayer = Math.max(maxLayer, m.last.layer);
    minTimestamp = Math.min(minTimestamp, m.startTimestamp);
    maxTimestamp = Math.max(maxTimestamp, m.endTimestamp);
    GitWorks.addUnique(nodes, m.first);
    GitWorks.addUnique(nodes, m.last);
    t = twins.get(m.first.id.getName() + m.last.id.getName());
    if (t != null) {
      parallels.put(m, t.toArray(new MetaEdge[0]));
      // numParallels += t.size() - 1; XXX how many parallel edges not in mEdges
    }
  }
  numParallels = parallels.keySet().size(); // XXX how many groups of parallel edges
  if (numParallels == 0) parallels = null;
  mNodes = nodes.toArray(new Commit[0]);
  Collections.sort(mEdges);
  getTotEdges();
  getWeight();
}


private void getTotEdges() {
  MetaEdge[] ma;
  totEdges = 0;
  for (MetaEdge m : mEdges) {
    ma = parallels == null ? null : parallels.get(m);
    if (ma != null)
      totEdges += ma.length;
    else
      totEdges++;
  }
}


private void getWeight() {
  MetaEdge[] ma;
  weight = 0;
  for (MetaEdge m : mEdges) {
    ma = parallels == null ? null : parallels.get(m);
    if (ma != null) {
      for (MetaEdge mm : ma)
        weight += mm.getWeight();
    } else
      weight += m.getWeight();
  }
}


int getTotCommits() {
  return weight + mNodes.length;
}


boolean hasParallels() {
  return parallels != null;
}


MetaEdge[] getAllEdges() {
  ArrayList<MetaEdge> res = new ArrayList<MetaEdge>();
  MetaEdge[] ma;
  for (MetaEdge m : mEdges) {
    ma = parallels == null ? null : parallels.get(m);
    if (ma != null) {
      for (MetaEdge mm : ma)
        res.add(mm);
    } else
      res.add(m);
  }
  return res.toArray(new MetaEdge[0]);
}


@Override
public int compareTo(MotifOccurrence mo) {
  int res = minLayer - mo.minLayer;
  return res == 0 ? maxLayer - mo.maxLayer : res;
}

}
