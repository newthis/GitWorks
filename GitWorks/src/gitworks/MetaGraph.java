package gitworks;


import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.eclipse.jgit.lib.ObjectId;


public class MetaGraph implements Externalizable {

static class NodeDegreeComparator implements java.util.Comparator<Commit> {

  @Override
  public int compare(Commit c1, Commit c2) {
    int res = (c2.outDegree + c2.inDegree) - (c1.outDegree + c1.inDegree);
    if (res == 0) res = c2.outDegree - c1.outDegree;
    if (res == 0) res = c2.inDegree - c1.inDegree;
    return res;
  }

}


private int maxID;

ArrayList<Commit> allCommits;
ArrayList<Dag> dags;
long since;
long until;
private int diameter; // the number of metaedges in the longest path from the oldest root
private int maxWidth; // the largest number of metaedges belonging to the same layer
private int[] layerSizes; // how many metaedges per layer


private MetaGraph() {
  maxID = 0;
  allCommits = new ArrayList<Commit>();
  dags = new ArrayList<Dag>();
  since = Long.MAX_VALUE;
  until = 0L;
  diameter = 0;
  maxWidth = 0;
  layerSizes = null;
}


public MetaGraph(ArrayList<Commit> all) {
  maxID = 0;
  allCommits = all;
  dags = new ArrayList<Dag>();
  since = Long.MAX_VALUE;
  until = 0L;
  diameter = 0;
  maxWidth = 0;
  layerSizes = null;
}


int getDiameter() {
  if (diameter == 0) getLayerSizes();
  return diameter;
}


int getMaxWidth() {
  if (maxWidth == 0) getLayerSizes();
  return maxWidth;
}


MetaEdge getEdge(int id) {
  MetaEdge res = null;
  for (Dag d : dags) {
    res = d.getEdge(id);
    if (res != null) break;
  }
  return res;
}


int[] getSummaryStats() {
  int[] dRes, res = new int[8];
  Arrays.fill(res, 0);
  int i;
  for (Dag d : dags) {
    dRes = d.getSummaryStats();
    i = 0;
    for (int r : dRes)
      res[i++] += r;
  }
  return res;
}


DescriptiveStatistics getInternalCommitStats() {
  DescriptiveStatistics ds = new DescriptiveStatistics();
  for (Dag d : dags)
    d.getInternalCommitStats(ds);
  return ds;
}


DescriptiveStatistics getMetaEdgeAuthorStats() {
  DescriptiveStatistics ds = new DescriptiveStatistics();
  for (Dag d : dags)
    d.getMetaEdgeAuthorStats(ds);
  return ds;
}


DescriptiveStatistics getLayerStats() {
  DescriptiveStatistics ds = new DescriptiveStatistics();
  if (layerSizes == null) getLayerSizes();
  if (layerSizes != null)
    for (int s : getLayerSizes())
      ds.addValue((double)s);
  return ds;
}


int[] getLayerSizes() {
  if (layerSizes != null) return layerSizes;
  int[][] m = new int[dags.size()][];
  int i = 0;
  diameter = 0;
  maxWidth = 0;
  for (Dag d : dags) {
    m[i] = d.getLayerSizes();
    diameter = Math.max(diameter, d.getDiameter());
    maxWidth = Math.max(maxWidth, d.getMaxWidth());
    i++;
  }
  if (diameter == 0) return null;
  layerSizes = new int[diameter];
  Arrays.fill(layerSizes, 0);
  for (int[] vals : m) {
    if (vals == null) continue;
    i = 0;
    for (int v : vals) {
      layerSizes[i] += v;
      i++;
    }
  }
  return layerSizes;
}


/**
 * Create a meta-graph composing the given set of edges in a proper set of dags. No check is
 * performed on the arguments. Thus, being they not correct and consistent with each other, the
 * resulting meta-graph will be uncorrect.
 * 
 * @param allEdges
 * @param allComs
 * @param heads
 * @return
 */
static MetaGraph createMetaGraph(ArrayList<MetaEdge> allEdges, ArrayList<Commit> allComs,
    ArrayList<Commit> heads) {
  long tStamp;
  Dag d, d1;
  Commit co;
  MetaEdge me;
  ArrayList<Commit> cur = new ArrayList<Commit>();
  MetaGraph res = new MetaGraph(allComs);
  for (Commit c : heads) {
    d = new Dag();
    cur.add(c);
    d1 = null;
    do {
      co = cur.remove(0);
      for (int e : co.edges) {
        me = GitWorks.getElement(allEdges, e);
        if (co.equals(me.first)) continue;
        if (d1 == null) d1 = res.getDag(e);
        if (d1 != null && d1 != d) {
          d1.union(d);
          d = d1;
          break;
        } else {
          GitWorks.addUnique(cur, me.first);
          d.addEdge(me);
          res.maxID = Math.max(res.maxID, me.ID);
        }
      }
      if (co.inDegree > 0 && co.outDegree == 0) {
        GitWorks.addUnique(d.leaves, co);
      } else if (co.inDegree == 0) {
        GitWorks.addUnique(d.roots, co);
      } else {
        GitWorks.addUnique(d.nodes, co);
      }
      tStamp = co.getCommittingInfo().getWhen().getTime();
      if (res.until < tStamp) res.until = tStamp;
      if (res.since > tStamp) res.since = tStamp;
    } while (!cur.isEmpty());
    if (d1 == null) res.dags.add(d);
  }
  // res.checkup();
  return res;
}


/**
 * Create a meta-graph containing the given commits.
 * 
 * @param allComs
 * @param heads
 * @return
 */
static MetaGraph createMetaGraph(ArrayList<Commit> allComs, ArrayList<Commit> heads) {
  MetaGraph res = new MetaGraph(allComs);
  long tStamp;
  for (Commit c : heads) {
    if (c.edges.isEmpty()) { // was not found in previous iterations
      res.addHead(c);
    }
  }
  for (Commit c : heads) {
    if (c.inDegree > 0 && c.outDegree == 0) {
      GitWorks.addUnique(res.getDag(c.edges.get(0)).leaves, c);
      tStamp = c.getCommittingInfo().getWhen().getTime();
      if (res.until < tStamp)
        res.until = tStamp;
      if (res.since > tStamp)
        res.since = tStamp;
    }
  }
  for (Dag d : res.dags)
    d.bfVisit();
  return res;
}


private Dag getDag(int edgeId) {
  for (Dag d : dags)
    if (d.getEdge(edgeId) != null)
      return d;
  return null;
}


/**
 * See comments for Dag.buildSubGraph .
 * 
 * @param minAge
 * @param maxAge
 * @return
 */
MetaGraph buildSubGraph(Date minAge, Date maxAge) {
  MetaGraph res, subs[] = new MetaGraph[dags.size()];
  for (int i = 0; i < dags.size(); i++) {
    subs[i] = dags.get(i).buildSubGraph(minAge, maxAge);
  }
  res = new MetaGraph();
  for (MetaGraph m : subs) {
    maxID = Math.max(maxID, m.maxID);
    since = Math.min(since, m.since);
    until = Math.max(until, m.until);
    dags.addAll(m.dags);
    allCommits.addAll(m.allCommits);
  }
  Collections.sort(allCommits);
  return res;
}


Dag getOldestDag() {
  int i, oIndx = 0;
  java.util.Date cur, oldest = new java.util.Date(Long.MAX_VALUE);
  for(i = 0; i < dags.size(); i++) {
    for (Commit c : dags.get(i).roots) {
      cur = c.getCommittingInfo().getWhen();
      if (cur.before(oldest)) {
        oldest = cur;
        oIndx = i;
      }
    }
  }
  return dags.get(oIndx);
}


Dag getLargestDag() {
  int i, bi = 0, cur, best = 0;
  for (i = 0; i < dags.size(); i++) {
    cur = dags.get(i).getNumCommits();
    if (cur > best) {
      best = cur;
      bi = i;
    }
  }
  return dags.get(bi);
}


Dag getDensierDag() {
  int i, bi = 0, cur, best = 0;
  for (i = 0; i < dags.size(); i++) {
    cur = dags.get(i).getNumMetaEdges();
    if (cur > best) {
      best = cur;
      bi = i;
    }
  }
  return dags.get(bi);
}


private void absorbeEdge(Dag d, Commit c, MetaEdge me) {
  MetaEdge curMe = d.removeEdge(c.edges.remove(0));
  Commit c2;
  me.addInternal(c);
  for (int i = 0; i < curMe.getWeight(); i++) {
    c2 = curMe.getInternals().get(i);
    me.addInternal(c2);
    c2.edges.remove(0);
    c2.edges.add(me.ID);
  }
  me.first = curMe.first;
  me.first.edges.remove(Collections.binarySearch(me.first.edges, curMe.ID));
  me.first.edges.add(me.ID);
  GitWorks.addUnique(c.edges, me.ID); // to make the procedure self-contained
}


private void splitEdge(Dag d, Commit c, int newID) {
  MetaEdge newMe, me = d.getEdge(c.edges.get(0));
  Commit c2 = me.first;
  me.first = c;
  newMe = new MetaEdge(newID);
  d.addEdge(newMe);
  newMe.last = c;
  newMe.first = c2;
  c2.edges.remove(Collections.binarySearch(c2.edges, me.ID));
  GitWorks.addUnique(c2.edges, newMe.ID);
  int z = me.getInternals().indexOf(c);
  while (z < me.getWeight() - 1) {
    c2 = me.getInternals().remove(z + 1);
    newMe.addInternal(c2);
    c2.edges.remove(0);
    c2.edges.add(newMe.ID);
  }
  me.getInternals().remove(z);
  GitWorks.addUnique(c.edges, newMe.ID);
}


private Commit[] nextGen(Commit c) {
  Commit pc;
  Commit[] res;
  ArrayList<Commit> parents = new ArrayList<Commit>();
  for (ObjectId p : c.getParents()) {
    pc = GitWorks.getElement(allCommits, p);
    if (pc != null)
      parents.add(pc);
  }
  res = new Commit[parents.size() + 1];
  res[0] = c;
  for(int i = 1; i < res.length; i++)
    res[i] = parents.get(i - 1);
  return res;
}


private Commit[] addCommit(Dag d, Commit c, MetaEdge me) {
  Commit[] parents;
  Commit res[] = null;
  c.outDegree++;
  if (c.edges.isEmpty()) { // c has never been considered before
    parents = nextGen(c);
    c.inDegree = parents.length - 1;
    if (c.inDegree == 0 || c.inDegree > 1)
      me.first = c; // first commit of the repo or merge commit
    else
      me.addInternal(c);
    if (c.inDegree == 1) { // simple commit: chain it with its parent
      GitWorks.addUnique(c.edges, me.ID);
      return addCommit(d, parents[1], me);
    } else { // return the commit and its list of parents
      res = parents;
    }
  } else { // c has already been considered in previous calls
    Dag d1 = getDag(c.edges.get(0));
    if (d1 == null) d1 = d;
    if (c.outDegree == 1 && c.inDegree == 1) { // terminal commit: change it to internal
      absorbeEdge(d1, c, me);
    } else if (c.outDegree == 2 && c.inDegree == 1) {
      // internal commit: change it to terminal
      me.first = c;
      splitEdge(d1, c, ++maxID);
    } else { // branch commit or merge commit
      me.first = c;
    }
    if (d.union(d1))
      dags.remove(dags.indexOf(d1));
    res = new Commit[] {me.first};
  }
  GitWorks.addUnique(c.edges, me.ID);
  return res;
}


// must NOT be called more than once for the same commit!
private void addHead(Commit c) {
  Commit[] p, cur;
  MetaEdge me; Commit co;
  ArrayList<Commit[]> next = new ArrayList<Commit[]>();
  long tStamp;
  p = nextGen(c);
  Dag d = new Dag();
  // if c points the first commit of the repo, just return
  if (p.length == 1) {
    if (!dags.isEmpty()) {
      for (Dag d1 : dags)
        if (Collections.binarySearch(d1.roots, c) >= 0)
          return;
    }
    GitWorks.addUnique(d.roots, c);
    tStamp = c.getCommittingInfo().getWhen().getTime();
    if (since > tStamp)
      since = tStamp;
    if (until < tStamp)
      until = tStamp;
    dags.add(d);
    return; 
  }
  next.add(p);
  do {
    cur = next.remove(0);
    co = cur[0];
    co.inDegree = cur.length - 1;
    for (int i = 1; i < cur.length; i++) {
      me = new MetaEdge(++maxID);
      me.last = co;
      GitWorks.addUnique(co.edges, me.ID);
      p = addCommit(d, cur[i], me);
      if (p[0].inDegree == 0) {
        if (!dags.isEmpty()) {
          for (Dag d1 : dags)
            if (Collections.binarySearch(d1.roots, p[0]) >= 0) {
              d.union(d1);
              dags.remove(dags.indexOf(d1));
              break;
            }
        }
        GitWorks.addUnique(d.roots, p[0]);
      } else {
        GitWorks.addUnique(d.nodes, p[0]);
      }
      tStamp = p[0].getCommittingInfo().getWhen().getTime();
      if (since > tStamp)
        since = tStamp;
      if (until < tStamp)
        until = tStamp;
      d.addEdge(me);
      if (p.length > 1) {
        next.add(p);
      }
    }
  } while (!next.isEmpty());
  dags.add(d);
}


boolean checkup() {
  boolean res;
  int max = 0;
  dags.trimToSize();
  res = allCommits == null || allCommits.size() == 0;
  if (res)
    System.err.println("MetaGraph: ERROR : allCommits array is not set.");
  else {
    for (Dag d : dags) {
      max += d.getNumMetaEdges();
      res = res || !d.checkup(true);
    }
    if (res)
      System.err.println("MetaGraph : ERROR : inconsistent dags.");
    else {
      res = res || (maxID < max);
      if (res) System.err.println("MetaGraph : ERROR : maxID is too low.");
    }
//    if (res) {
//      System.err.println("MetaGraph : allCommits :");
//      GitWorks.printAny(allCommits, "\n", System.err);
//    }
  }
  return !res;
}


@Override
public String toString() {
  int i = 1;
  String res = "", out[];
  if (dags.isEmpty())
    return "not been defined yet.";
  else
    out = new String[dags.size() + 1];
  int roots = 0, leaves = 0, nodes = 0, edges = 0, tot = 0;
  for (Dag d : dags) {
    roots += d.roots.size();
    leaves += d.leaves.size();
    nodes += d.nodes.size();
    edges += d.getNumMetaEdges();
    tot += d.getNumCommits();
    out[i] = "\tDag " + i + " : " + d.toString();
    i++;
  }
  out[0] = dags.size() + " dag" + (dags.size() > 1 ? "s, " : ", ") + roots + " roots, " + leaves + " leaves, "
      + nodes + " nodes, " + edges + " meta-edges for " + tot + " total commits" + (dags.size() > 1 ? ", distributed as follows:\n" : ".");
  if (out.length > 2)
    for (String s : out)
      res += s;
  else
    res = out[0];
  return res;
}


@Override
public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  MetaEdge e;
  int i, j, z, size, dagsNum = in.readInt();
  if (dagsNum == 0) return;
  since = in.readLong();
  until = in.readLong();
  maxID = in.readInt();
  for (int k = 0; k < dagsNum; k++) {
    Dag d = new Dag();
    dags.add(d);
    size = in.readInt();
    for (i = 0; i < size; i++) {
      d.roots.add(allCommits.get(in.readInt()));
    }
    size = in.readInt();
    for (i = 0; i < size; i++) {
      e = new MetaEdge(in.readInt());
      e.layer = in.readInt();
      e.first = allCommits.get(in.readInt());
      e.last = allCommits.get(in.readInt());
      j = in.readInt();
      for (z = 0; z < j; z++)
        e.addInternal(allCommits.get(in.readInt()));
      if (e.getWeight() > 0) e.getInternals().trimToSize();
      d.addEdge(e);
    }
    size = in.readInt();
    for (i = 0; i < size; i++) {
      d.leaves.add(allCommits.get(in.readInt()));
    }
    size = in.readInt();
    for (i = 0; i < size; i++) {
      d.nodes.add(allCommits.get(in.readInt()));
    }
    d.checkup(false);
  }
}


@Override
public void writeExternal(ObjectOutput out) throws IOException {
  MetaEdge me;
  out.writeInt(dags.size());
  if (dags.isEmpty()) {
    out.flush();
    return;
  }
  out.writeLong(since);
  out.writeLong(until);
  out.writeInt(maxID);
  for (Dag d : dags) {
    out.writeInt(d.roots.size());
    Iterator<Commit> itc = d.roots.iterator();
    while (itc.hasNext()) {
      out.writeInt(Collections.binarySearch(allCommits, itc.next()));
    }
    out.writeInt(d.getNumMetaEdges());
    Iterator<MetaEdge> ite = d.getMetaEdges();
    while (ite.hasNext()) {
      me = ite.next();
      out.writeInt(me.ID);
      out.writeInt(me.layer);
      out.writeInt(Collections.binarySearch(allCommits, me.first));
      out.writeInt(Collections.binarySearch(allCommits, me.last));
      out.writeInt(me.getWeight());
      if (me.getWeight() > 0) for (Commit c : me.getInternals())
        out.writeInt(Collections.binarySearch(allCommits, c));
    }
    out.writeInt(d.leaves.size());
    itc = d.leaves.iterator();
    while (itc.hasNext()) {
      out.writeInt(Collections.binarySearch(allCommits, itc.next()));
    }
    out.writeInt(d.nodes.size());
    itc = d.nodes.iterator();
    while (itc.hasNext()) {
      out.writeInt(Collections.binarySearch(allCommits, itc.next()));
    }
  }
  out.flush();
}

}
