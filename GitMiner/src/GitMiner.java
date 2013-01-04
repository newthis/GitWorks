import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.TreeWalk;


public class GitMiner {

public static String prefix = "JGIT_"; // to be prepended to any jgit-generated output file name
public static String field_sep = "    "; // field separator in input datafile's lines
public static String id_sep = "/"; // the string that separates owner and name in a fork id string
public static String list_sep = ","; // fork id separator in the list taken from the input dataset
                                     // file
public static String log_sep = "<#>"; // field separator within a git log output line
public static String repo_dir; // the absolute path to the dir that contains the git repos to be
                               // imported in jgit data structures
public static String[] ids = null; // list of repos to be considered to build the fork tree and
                                   // perform analysis.
public static String gits_out_dir; // the relative path to the dir which will contain the
                                   // jgit-generated git repos to analyse
public static String trees_out_dir; // the relative path to the dir which will contain the
                                    // jgit-generated trees of the repos

// this operator requires a Git object as parameter
static DfsOperator addAsRemote = new DfsOperator() {

  public int getID() {
    return 2;
  }


  public boolean runOnce() {
    return true;
  }


  public void initialize(ForkEntry fe) {}


  public void run(ForkEntry fe, Object arg) throws Exception {
    Git git = (Git)arg;
    RefSpec all;
    String fork = getProjectNameAsRemote(fe);
    // System.out.print("Adding " + fork + " as mainline ...");
    // System.out.flush();
    StoredConfig config = git.getRepository().getConfig();
    config.setString("remote", fork, "url", "file:///" + getProjectPath(fe));
    config.setString("remote", fork, "fetch", "+refs/heads/*:refs/remotes/" + fork + "/*");
    config.save();
    all = new RefSpec(config.getString("remote", fork, "fetch"));
    git.fetch().setRemote(fork).setRefSpecs(all).call();
    // System.out.println(" done!");
    // System.out.flush();
  }


  public void finalize(ForkEntry fe) {}
};


// TODO: factorize dfsVists
static void dfsVisit(int depth, ForkEntry f, DfsOperator t, ForkList l) throws Exception {
  if (t == null) {
    System.err.println("WARNING: dfsVisit called with null operator.");
    return;
  }
  if (f == null) {
    System.err.println("WARNING: DfsOperator " + t.getID() + " called on a null instance.");
    return;
  }
  if (l == null) {
    System.err.println("WARNING: DfsOperator " + t.getID() + " called with a null argument.");
    return;
  }
  if (depth > 0 && f.hasForks()) {
    t.initialize(f);
    Iterator<ForkEntry> it = f.getForks();
    while (it.hasNext()) {
      dfsVisit(depth - 1, it.next(), t, l);
      if (!t.runOnce()) t.run(f, l);
    }
    if (t.runOnce()) t.run(f, l);
  } else {
    t.run(f, l);
  }
  t.finalize(f);
}


static void dfsVisit(int depth, ForkEntry f, DfsOperator t, Git git) throws Exception {
  if (t == null) {
    System.err.println("WARNING: dfsVisit called with null operator.");
    return;
  }
  if (f == null) {
    System.err.println("WARNING: DfsOperator " + t.getID() + " called on a null instance.");
    return;
  }
  if (git == null) {
    System.err.println("WARNING: DfsOperator " + t.getID() + " called with a null argument.");
    return;
  }
  if (depth > 0 && f.hasForks()) {
    t.initialize(f);
    Iterator<ForkEntry> it = f.getForks();
    while (it.hasNext()) {
      dfsVisit(depth - 1, it.next(), t, git);
      if (!t.runOnce()) t.run(f, git);
    }
    if (t.runOnce()) t.run(f, git);
  } else {
    t.run(f, git);
  }
  t.finalize(f);
}


static void dfsVisit(int depth, ForkEntry f, DfsOperator t, int[] t_arg) throws Exception {
  if (t == null) {
    System.err.println("WARNING: dfsVisit called with null operator.");
    return;
  }
  if (f == null) {
    System.err.println("WARNING: DfsOperator " + t.getID() + " called on a null instance.");
    return;
  }
  if (t_arg == null) {
    System.err.println("WARNING: DfsOperator " + t.getID() + " called with a null argument.");
    return;
  }
  if (depth > 0 && f.hasForks()) {
    t.initialize(f);
    int[] temp = new int[t_arg.length];
    Iterator<ForkEntry> it = f.getForks();
    while (it.hasNext()) {
      System.arraycopy(t_arg, 0, temp, 0, t_arg.length);
      dfsVisit(depth - 1, it.next(), t, temp);
      if (!t.runOnce()) t.run(f, temp);
    }
    if (t.runOnce()) t.run(f, temp);
    System.arraycopy(temp, 0, t_arg, 0, t_arg.length);
  } else {
    t.run(f, t_arg);
  }
  t.finalize(f);
}


static TreeWalk makeTree(RevWalk walk, AnyObjectId ref) throws Exception {

  TreeWalk treeWalk = new TreeWalk(walk.getObjectReader());
  // treeWalk.setRecursive(true);
  treeWalk.addTree(walk.parseTree(walk.parseAny(ref)));
  walk.reset();
  return treeWalk;
}


// checkout from a given jgit tree pointer
static void createTree(ForkEntry fe, TreeWalk treeWalk) throws Exception {

  String path;
  ObjectReader reader = treeWalk.getObjectReader();
  ObjectLoader loader = null;
  PrintStream pout = null;
  ObjectId objId;
  PrintWriter perr = null;

  // System.out.println("Getting into a new tree of depth " + treeWalk.getDepth());
  while (treeWalk.next()) {
    // for (int k = 0; k < treeWalk.getTreeCount(); k++) {
    objId = treeWalk.getObjectId(0); // k 0
    path = treeWalk.isRecursive() ? treeWalk.getNameString() : treeWalk.getPathString();
    try {
      loader = reader.open(objId);
    }
    catch (Exception e) {
      if (perr == null)
        perr = new PrintWriter(new FileWriter(trees_out_dir + "/" + getProjectNameAsRemote(fe)
            + "/" + prefix + "errors.log"), true);
      // e.printStackTrace(perr);
      if (objId.equals(ObjectId.zeroId()))
        perr.println("Object " + objId.getName() + " (" + path + ") is all-null!");
      else
        perr.println("Object " + objId.getName() + " (" + path + ") does not exist!");
      continue;
    }
    if (loader.getType() == Constants.OBJ_BLOB) {
      pout = new PrintStream(new FileOutputStream(new File(trees_out_dir + "/"
          + getProjectNameAsRemote(fe) + "/" + path), false), true);
      loader.copyTo(pout);
      pout.close();
    } else if (treeWalk.isSubtree()) { // loader.getType() == Constants.OBJ_TREE
      if ((new File(trees_out_dir + "/" + getProjectNameAsRemote(fe) + "/" + path)).mkdirs()) {
        treeWalk.enterSubtree();
        // System.out.println("Getting into a new tree of depth " + treeWalk.getDepth());
      } else {
        if (perr == null)
          perr = new PrintWriter(new FileWriter(trees_out_dir + "/" + getProjectNameAsRemote(fe)
              + "/" + prefix + "errors.log"), true);
        perr.println("Dir " + path + "(Object " + objId.getName() + ") could not be created!");
      }
    }
    // }
  }
  treeWalk.reset();
}


// printout all commit messages in a given range -> use and reset an existing RevWalk
static void printCommits(String outFile, RevWalk walk) throws IOException, NoHeadException,
    GitAPIException {

  PrintWriter pout = new PrintWriter(new FileWriter(outFile), true);
  RevCommit c = walk.next();
  while (c != null && !c.has(RevFlag.UNINTERESTING)) {
    pout.println(printCommit(c));
    c = walk.next();
  }
  walk.reset();
  pout.close();
}


// printout all commit messages in a given range -> expensive: creates a one-time only RevWalk
static void printCommits(String outFile, Git git, String from_ref, String to_ref)
    throws IOException, NoHeadException, GitAPIException {

  AnyObjectId from = git.getRepository().resolve(from_ref);
  AnyObjectId to = (to_ref == null || to_ref.equals("")) ? null : git.getRepository().resolve(
      to_ref);
  RevWalk walk = new RevWalk(git.getRepository());
  // walk.sort(RevSort.COMMIT_TIME_DESC);
  // walk.sort(RevSort.REVERSE);
  walk.markStart(walk.parseCommit(from));
  if (to != null) walk.markUninteresting(walk.parseCommit(to));

  printCommits(outFile, walk);
  // PrintWriter pout = new PrintWriter(new FileWriter(outFile), true);
  // Iterator<RevCommit> itc = git.log().add(from).call().iterator();
  // RevCommit c;
  // while (itc.hasNext()) {
  // c = itc.next();
  // pout.println("===========\n" + printCommit(c));
  // if (to != null && c.equals(to)) break;
  // }
  // pout.close();
}


// format the output like in --pretty="%H<#>%aN<#>%at<#>%cN<#>%ct<#>%s
static String printCommit(RevCommit c) {
  String out = "";
  PersonIdent author = c.getAuthorIdent();
  PersonIdent committer = c.getCommitterIdent();
  // long commitTime = (long)c.getCommitTime() == committer.getWhen().getTime() / 1000 (in seconds)
  out += "" + c.name()
      + log_sep + author.getName() + log_sep + author.getWhen().getTime()
      + log_sep + committer.getName() + log_sep + committer.getWhen().getTime()
      + log_sep + c.getShortMessage(); //c.getFullMessage(); c.getShortMessage();
  return out;
}


// print some structural info about the git repo
static String printRepoInfo(Repository repository) {

  String out = "Current GIT_DIR : " + repository.getDirectory().getAbsolutePath() + "\n";
  StoredConfig config = repository.getConfig();
  Set<String> sections = config.getSections();
  Set<String> subsections;
  out += "This repository has " + sections.size() + " sections:\n";
  for (String s : sections) {
    out += s + " : <";
    subsections = config.getSubsections(s);
    for (String ss : subsections)
      out += ss + "  ";
    out += ">\n";
  }
  return out;
}


// import a git repo in jgit data structures or create a new one
static Repository createRepo(String repoDir, String gitDir, boolean anew, boolean bare)
    throws IOException {

  File gd = new File(gitDir);
  File rd = new File(repoDir);
  if (anew) {
    if (rd.exists()) FileUtils.delete(rd, FileUtils.RECURSIVE);
    rd.mkdirs();
    if (gd.exists()) FileUtils.delete(gd, FileUtils.RECURSIVE);
    gd.mkdirs();
  }
  Repository repository = new FileRepositoryBuilder().setWorkTree(rd).setGitDir(gd)
      .readEnvironment() // scan environment GIT_* variables
      .findGitDir() // scan up the file system tree
      .setMustExist(!anew).build();
  if (anew) repository.create(bare);

  return repository;
}


// Returns the project ID formatted in a convenient way to serve as a remote name...
static String getProjectNameAsRemote(ForkEntry f) {
  return f.getId().replace("/", "--");
}


// It gives the absolute path (internal URI) of the repo corresponding to the given ForkEntry.
static String getProjectPath(ForkEntry f) {
  String t[] = f.getId().split(id_sep);
  return GitMiner.repo_dir + t[1] + "/" + t[0] + "/" + t[1] + ".git";
}


// add remotes to a jgit repo, using a given ForkEntry data structure
static void addRemotes(Git git, ForkEntry project, int depth) throws Exception {
  dfsVisit(depth, project, GitMiner.addAsRemote, git);
  git.getRepository().scanForRepoChanges();
}


// as of now, it is meant to compute things in the big fork tree of each project, so that for forks
// at different layers the computed aggregation depth is parent's one - 1.
static void computeAggregates(String ids[], ForkList fl, int depth) throws Exception {
  if (fl.size() == 0 || depth < 1) {
    System.err.println("computeAggregates : input ERROR.");
    return;
  }
  int[] r = new int[4];
  if (ids == null || ids.length == 0) {
    ids = new String[fl.size()];
    for (int i = 0; i < fl.size(); i++) {
      ids[i] = fl.get(i).getId();
    }
  }
  for (String id : ids) {
    if (!ForkEntry.isValidId(id)) {
      System.err.println("computeAggregates : input ERROR (invalid id: " + id + ").");
      continue;
    }
    Arrays.fill(r, 0);
    dfsVisit(depth, fl.get(id), ForkEntry.computeAggregates, r);
  }
}


// delete from the children ForkList of the argument all the entries whose repo cannot be found in
// the local FS.
static void purgeMissingForks(ForkList globalList, ForkEntry f) throws Exception {
  File fi;
  int c = 0; // String out = "";
  Iterator<ForkEntry> it = f.getForks();
  ForkEntry fe, fks[] = new ForkEntry[f.howManyForks()];
  while (it.hasNext()) {
    fe = it.next();
    fi = new File(getProjectPath(fe));
    if (!fi.canRead()) {
      fks[c++] = fe;
      // out += " " + fe.getId();
      globalList.remove(fe); // remove fe from the main projects list (no dangling entries)!
    }
  }
  // System.out.print("Deleting missing repos entries from the lists (" + out + " ) ... ");
  f.removeForks(Arrays.copyOf(fks, c));
  // System.out.println("done!");
}


static ForkList populateForkList(String inputFile) throws Exception {

  ForkEntry fe, fc;
  String line, tokens[];
  int c = 0, cc = 0;
  ArrayList<String> children = new ArrayList<String>();
  BufferedReader listFile = new BufferedReader(
      new InputStreamReader(new FileInputStream(inputFile)));
  ForkList l = new ForkList();

  while ((line = listFile.readLine()) != null) {
    c++;
    tokens = line.split(field_sep);
    if (ForkEntry.isValidId(tokens[1] + id_sep + tokens[0])) {
      cc = l.add(new ForkEntry(tokens[1], tokens[0], Integer.valueOf(tokens[3])));
      if (cc < 0) {
        children.add(-cc - 1, tokens.length == 5 ? tokens[4] : "");
      } else {
        System.err.println("WARNING: duplicate entry in input file (" + tokens[1] + id_sep
            + tokens[0] + ").");
      }
    } else {
      System.err.println("Error while reading fork data from file, at line " + c + ".");
    }
  }
  listFile.close();
  Iterator<ForkEntry> it = l.getAll();
  for (int i = 0; it.hasNext(); i++) {
    fe = it.next();
    if (!"".equals(children.get(i))) {
      cc = 0;
      tokens = children.get(i).split(list_sep);
      for (String f : tokens) {
        cc++;
        fc = l.get(f);
        if (fc != null) {
          fe.addFork(fc);
        } else {
          System.err.println("Error while reading fork data from file, for project " + fe.getId()
              + " about fork # " + cc + " (" + f + ").");
        }
      }
    }
  }
  l.setTreeCounter();
  return l;
}


static void dumpFile(String filePath, ForkList l) throws IOException {
  File dump = new File(filePath);
  if (dump.exists()) dump.delete();
  GZIPOutputStream gzOut = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(dump)));
  ObjectOutput out = new ObjectOutputStream(gzOut);
  l.writeExternal(out);
  gzOut.finish();
  out.close();
}


static ForkList importForkList(String filePath) throws FileNotFoundException, IOException,
    ClassNotFoundException {
  ForkList res = new ForkList();
  ObjectInput in = new ObjectInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(filePath))));
  res.readExternal(in);
  return res;
}


public static void main(String[] args) throws Exception {

  boolean anew = true; // flag to differentiate tests
  boolean bare = false; // flag to differentiate tests
  ForkList projects;
  ForkEntry fe;
  RevWalk walk = null;
  Git git = null;

  if (args.length < 5) {
    System.err
        .println("Usage: java GitMiner <repo list file path> <repo dir path> <jgit gits out dir> <jgit trees out dir> <comma-separated no-space list of fork ids>");
    System.exit(2);
  }
  repo_dir = args[1].trim() + (args[1].trim().endsWith("/") ? "" : "/");
  ids = args[4].trim().split(",");
  gits_out_dir = args[2].trim() + (args[2].trim().endsWith("/") ? "" : "/");
  trees_out_dir = args[3].trim() + (args[3].trim().endsWith("/") ? "" : "/");
  if (!new File(repo_dir).isDirectory() || !(new File(trees_out_dir)).isDirectory()
      || !new File(gits_out_dir).isDirectory()) {
    System.err
        .println("FATAL ERROR : Cannot find repos dir (" + repo_dir + ") or gits output dir ("
            + gits_out_dir + ") or trees output dir (" + trees_out_dir + ")");
    System.exit(1);
  }

  /************** create fork list ****************/

  projects = populateForkList(args[0].trim());
  //projects = importForkList(trees_out_dir + "listDump");
  computeAggregates(ids, projects, 100); // with a large param value the complete fork trees will be
                                         // visited
  // dumpFile(trees_out_dir + "listDump", projects);
  // computeAggregates(null, projects, 1); // reset all projects aggregates
  System.out.println(projects.toString());

  /************** create/load git repo ****************/

  fe = projects.get(ids[0]);
  String gitDirPath = gits_out_dir + getProjectNameAsRemote(fe)
      + ((bare == true) ? ".git" : "/.git");
  String treeDirPath = trees_out_dir + getProjectNameAsRemote(fe);
  String refspec = "refs/remotes/" + getProjectNameAsRemote(fe) + "/master";
  try {
    // with git.init() it is not possible to specify a different tree path!!
    // git = Git.init().setBare(bare).setDirectory(new File(gitDirPath)).call();
    git = Git.wrap(createRepo(treeDirPath, gitDirPath, anew, bare));
    // System.out.println(printRepoInfo(git.getRepository()));

    /************** create big tree ****************/

    purgeMissingForks(projects, fe); // IRREVERSIBLE!!!
    addRemotes(git, fe, 100); // with a large param value the complete fork tree will be built

    /************** print commits & checkout ****************/

    printCommits(trees_out_dir + prefix + getProjectNameAsRemote(fe) + "-commitList.log", git,
        refspec, null);
    if (!bare) {
      git.checkout().setStartPoint(refspec).setCreateBranch(anew)
          .setName(getProjectNameAsRemote(fe)).call(); // .getResult() for a dry-run
      // createTree(fe, makeTree(walk, from));
    }

  }
  catch (Exception e) {
    e.printStackTrace();
  }
  finally {
    if (walk != null) walk.release();
    if (git != null) git.getRepository().close();
  }
}

}
