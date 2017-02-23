package jtdiff.main;

import jtdiff.util.Tree;
import jtdiff.util.TreeNode;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/* This file has the implementation of the algorithm described in
 * "The Tree-to-Tree Correction Problem" by Kuo-Chung Tai published
 * at the Journal of the ACM , 26(3):422-433, July 1979.
 *
 * We follow the naming of the variables and functions from the paper
 * even though sometimes it may be against some Python conventions.
 * The algorithm is at section 5 in the paper. There is one
 * missing piece in the algorithm provided in the paper which is
 * MIN_M(i, 1) and MIN_M(1, j) values. We added the computation
 * of these in the implementation below.
 */
public class TreeDiff {
  private static final int INFINITE = Integer.MAX_VALUE;

  // Constant used for describing insertions or deletions
  // It represents an invalid preorder position in the tree.
  private static final TreeNode ALPHA = null;
  public static final int ALPHA_INT = -1;

  /**
   * Returns the cost of transforming a to b
   *
   * @parameter a the label of the source node
   * @parameter b the label of the target node
   * @returns integer
   */
  protected static int r(TreeNode a, TreeNode b) {
    if (a != ALPHA && b != ALPHA && a.label().equals(b.label())) { // No change
      return 0;
    }
    return 1; // Insert, Delete, Change
  }

  protected static String keyForE(int s, int u, int i, int t, int v, int j) {
    return String.format("%d:%d:%d, %d:%d:%d", s, u, i, t, v, j);
  }

  /**
   * Returns the E mapping. Check the paper to understand what
   * the mapping mean.
   *
   * @parameter sourceTree the source tree (Tree)
   * @parameter targetTree the target tree (Tree)
   * @returns (dict, dict)
   *       The first dict is in the format {'i:j:k, p:q:r' => cost} where 
   *       i, j, k, p, q, r are integers. The second dict is in the format
   *       {'i:j:k, p:q:r' => mapping} where mapping is a list of
   *       (x, y) pairs showing which node at the preorder position x
   *       in the source tree is mapped to which node at the preorder
   *       position y in the target tree. If x is ALPHA, then it shows
   *       the node at the preorder position y in the target tree is
   *       inserted. If y is ALPHA, then it shows the node at the preorder
   *       position x in the souce tree is deleted.
   */
  protected static DictionaryPair computeE(Tree sourceTree, Tree targetTree) {
    Map<String, Integer> E = new HashMap<String, Integer>();
    Map<String, MappingList> mappingForE = new HashMap<String, MappingList>();
    DictionaryPair dictionaryPair = new DictionaryPair(E, mappingForE);
    for (int i = 1; i <= sourceTree.size(); i++) {
      for (int j = 1; j <= targetTree.size(); j++) {
        for (int u : sourceTree.ancestors(i)) {
          for (int s : sourceTree.ancestors(u)) {
            for (int v : targetTree.ancestors(j)) {
              for (int t : targetTree.ancestors(v)) {
                String key = keyForE(s, u, i, t, v, j);
                if ((s == u && u == i) && (t == v && v == j)) {
                  E.put(key, r(sourceTree.nodeAt(i), targetTree.nodeAt(j)));
                  mappingForE.put(key, new MappingList(i, j));
                }
                else if ((s == u && u == i) || (t < v && v == j)) {
                  int f_j = targetTree.fatherOf(j).preorderPosition();
                  String dependentKey = keyForE(s, u, i, t, f_j, j - 1);
                  E.put(key, E.get(dependentKey) + r(ALPHA, targetTree.nodeAt(j)));
                  // Insertion
                  mappingForE.put(
                    key, mappingForE.get(dependentKey).clone().add(ALPHA_INT, j));
                }
                else if ((s < u && u == i) || (t == v && v == j)) {
                  int f_i = sourceTree.fatherOf(i).preorderPosition();
                  String dependentKey = keyForE(s, f_i, i - 1, t, v, j);
                  E.put(key, E.get(dependentKey) + r(sourceTree.nodeAt(i), ALPHA));
                  // Deletion
                  mappingForE.put(
                    key, mappingForE.get(dependentKey).clone().add(i, ALPHA_INT));
                }
                else {
                  TreeNode xNode = sourceTree.childOnPathFromDescendant(u, i);
                  int x = xNode.preorderPosition();
                  TreeNode yNode = targetTree.childOnPathFromDescendant(v, j);
                  int y = yNode.preorderPosition();
                  String dependentKey1 = keyForE(s, x, i, t, v, j);
                  String dependentKey2 = keyForE(s, u, i, t, y, j);
                  String dependentKey3 = keyForE(s, u, x - 1, t, v, y - 1);
                  String dependentKey4 = keyForE(x, x, i, y, y, j);
                  int minDistance = Collections.min(
                      Arrays.asList(
                          E.get(dependentKey1),
                          E.get(dependentKey2),
                          E.get(dependentKey3) + E.get(dependentKey4)));
                  E.put(key, minDistance);
                  // Remember the mapping.
                  if (E.get(key).equals(E.get(dependentKey1))) {
                    mappingForE.put(key, mappingForE.get(dependentKey1).clone());
                  }
                  else if (E.get(key).equals(E.get(dependentKey2))) {
                    mappingForE.put(key, mappingForE.get(dependentKey2).clone());
                  }
                  else {
                    MappingList newMapping =
                        mappingForE
                            .get(dependentKey3)
                            .clone()
                            .extendWith(mappingForE.get(dependentKey4));
                    mappingForE.put(key, newMapping);
                  }
                }
              }
            }
          }
        }
      }
    }
    return dictionaryPair;
  }

  // Returns the key for MIN_M map
  protected static String keyForMIN_M(int s, int t) {
    return String.format("%d:%d", s, t);
  }

  /** 
   * Returns the MIN_M mapping. Check out the article to see
   * what the mapping mean
   *
   * @parameter E computed by computeE (dict)
   * @parameter sourceTree the source tree (Tree)
   * @parameter targetTree the target tree (Tree)
   * @returns (dict, dict)
   *        The first dict is the MIN_M map (key to cost). The second
   *        dict is (key to list of integer pairs) the transformation mapping
   *        where a pair (x, y) shows which node at the preorder position x
   *        in the source tree is mapped to which node at the preorder
   *        position y in the target tree. If x is ALPHA, then it shows
   *        the node at the preorder position y in the target tree is
   *        inserted. If y is ALPHA, then it shows the node at the preorder
   *        position x in the souce tree is deleted.
   */
  protected static DictionaryPair computeMIN_M(
      DictionaryPair dictionaryPair, Tree sourceTree, Tree targetTree) {
    Map<String, Integer> E = dictionaryPair.costs;
    Map<String, MappingList> mappingForE = dictionaryPair.mapping;

    Map<String, Integer> MIN_M = new HashMap<>();
    MIN_M.put(keyForMIN_M(1, 1), 0);
    Map<String, MappingList> mappingForMinM = new HashMap<>();
    mappingForMinM.put(keyForMIN_M(1, 1), new MappingList(1, 1));
    DictionaryPair newDictionaryPair =
        new DictionaryPair(MIN_M, mappingForMinM);

    // This part is missing in the paper
    for (int j = 2; j < targetTree.size(); j++) {
      MIN_M.put(
          keyForMIN_M(1, j),
          MIN_M.get(keyForMIN_M(1, j - 1)) +
              r(ALPHA, targetTree.nodeAt(j)));
      mappingForMinM.put(
          keyForMIN_M(1, j),
          mappingForMinM.get(keyForMIN_M(1, j - 1))
                        .clone()
                        .add(ALPHA_INT, j));
    }

    // This part is missing in the paper
    for (int i = 2; i < sourceTree.size(); i++) {
      MIN_M.put(keyForMIN_M(i, 1),
                MIN_M.get(keyForMIN_M(i - 1, 1)) +
                    r(sourceTree.nodeAt(i), ALPHA));
      mappingForMinM.put(
          keyForMIN_M(i, 1),
          mappingForMinM.get(keyForMIN_M(i - 1, 1))
                        .clone()
                        .add(i, ALPHA_INT));
    }

    for (int i = 2; i <= sourceTree.size(); i++) {
      for (int j = 2; j <= targetTree.size(); j++) {
        String keyForMIN_M_i_j = keyForMIN_M(i, j);
        MIN_M.put(keyForMIN_M_i_j, INFINITE);
        int f_i = sourceTree.fatherOf(i).preorderPosition();
        int f_j = targetTree.fatherOf(j).preorderPosition();
        
        for (int s : sourceTree.ancestors(f_i)) {
          for (int t : targetTree.ancestors(f_j)) {
            String dependentKeyForE = keyForE(s, f_i, i - 1, t, f_j, j - 1);
            String dependentKeyForM = keyForMIN_M(s, t);
            int temp = MIN_M.get(dependentKeyForM) +
                       E.get(dependentKeyForE) -
                       r(sourceTree.nodeAt(s), targetTree.nodeAt(t));
            MIN_M.put(keyForMIN_M_i_j,
                      Math.min(temp, MIN_M.get(keyForMIN_M_i_j)));
            if (temp == MIN_M.get(keyForMIN_M_i_j)) {
              mappingForMinM.put(
                  keyForMIN_M_i_j,
                  mappingForMinM
                      .get(dependentKeyForM)
                      .clone()
                      .extendUniquelyWith(
                          mappingForE.get(dependentKeyForE)));
            }
          }
        }

        MIN_M.put(
            keyForMIN_M_i_j,
            MIN_M.get(keyForMIN_M_i_j) +
            r(sourceTree.nodeAt(i), targetTree.nodeAt(j)));
        mappingForMinM.get(keyForMIN_M_i_j).add(i, j);
      }
    }
    return newDictionaryPair;
  }

  // Returns the key for D map
  protected static String keyForD(int i, int j) {
    return String.format("%d, %d", i, j);
  }

  /**
   * Returns the D mapping. Check out the article to see
   * what the mapping mean
   *
   * @parameter sourceTree the source tree (Tree)
   * @parameter targetTree the target tree (Tree)
   * @parameter MIN_M the MIN_M map (dict)
   * @parameter mappingForM the transformation details for MIN_M
   * @returns (dict, dict)
   *        The first dict is the D mapping (key to cost).
   *        The second dict is (key to list of integer pairs) the transformation mapping
   *        where a pair (x, y) shows which node at the preorder position x
   *        in the source tree is mapped to which node at the preorder
   *        position y in the target tree. If x is ALPHA, then it shows
   *        the node at the preorder position y in the target tree is
   *        inserted. If y is ALPHA, then it shows the node at the preorder
   *        position x in the souce tree is deleted.
   */
  public static DictionaryPair computeD(
      Tree sourceTree, Tree targetTree, DictionaryPair dictionaryPair) {
    Map<String, Integer> MIN_M = dictionaryPair.costs;
    Map<String, MappingList> mappingForMinM = dictionaryPair.mapping;
    Map<String, Integer> D = new HashMap<>();
    D.put(keyForD(1, 1), 0); 
    Map<String, MappingList> mappingForD = new HashMap<>();
    mappingForD.put(keyForD(1, 1), new MappingList(1, 1));
    DictionaryPair newDictionaryPair = new DictionaryPair(D, mappingForD);

    for (int i = 2; i <= sourceTree.size(); i++) {
      D.put(keyForD(i, 1),
            D.get(keyForD(i - 1, 1)) + r(sourceTree.nodeAt(i), ALPHA));
    }

    for (int j = 2; j <= targetTree.size(); j++) {
      D.put(keyForD(1, j),
            D.get(keyForD(1, j - 1)) + r(ALPHA, targetTree.nodeAt(j)));
      mappingForD.put(keyForD(1, j),
        mappingForD.get(keyForD(1, j - 1)).clone().add(ALPHA_INT, j));
    }

    for (int i = 2; i <= sourceTree.size(); i++) {
      for (int j = 2; j <= targetTree.size(); j++) {
        int option1 =
            D.get(keyForD(i, j - 1)) + r(ALPHA, targetTree.nodeAt(j));
        int option2 =
            D.get(keyForD(i - 1, j)) + r(sourceTree.nodeAt(i), ALPHA);
        int option3 = MIN_M.get(keyForMIN_M(i, j));
        D.put(keyForD(i, j),
              Collections.min(Arrays.asList(option1, option2, option3)));

        if (D.get(keyForD(i, j)) == option1) {
          mappingForD.put(
            keyForD(i,  j),
            mappingForD.get(keyForD(i, j - 1)).clone().add(ALPHA_INT, j));
        }
        else if (D.get(keyForD(i, j)) == option2) {
          mappingForD.put(
            keyForD(i,  j),
            mappingForD.get(keyForD(i - 1, j)).clone().add(i, ALPHA_INT));
        }
        else {
          mappingForD.put(
            keyForD(i,  j),
            mappingForMinM.get(keyForMIN_M(i, j)));
        }
      }
    }
    return newDictionaryPair;
  }

  /*
   * Produces a list of humand friendly descriptions for mapping
   * between two trees
   * Example:
   *   ['No change for A (@1)', 'Change from B (@2) to C (@3)',
   *    'No change for D (@3)', 'Insert B (@2)']
   *
   * @returns list of strings
   */
  public static List<String> produceHumanFriendlyMapping(
      MappingList mapping, Tree sourceTree, Tree targetTree) {
    List<String> humandFriendlyMapping = new ArrayList<>();
    for (IntPair ip : mapping.mList) {
      int i = ip.x, j = ip.y;
      if (i == ALPHA_INT) {
        TreeNode targetNode = targetTree.nodeAt(j);
        humandFriendlyMapping.add(
            String.format(
                "Insert %s (@%d)",
                targetNode.label(),
                targetNode.preorderPosition()));
      } else if (j == ALPHA_INT) {
        TreeNode sourceNode = sourceTree.nodeAt(i);
        humandFriendlyMapping.add(
          String.format(
              "Delete %s (@%d)",
              sourceNode.label(),
              sourceNode.preorderPosition()));
      } else {
        TreeNode sourceNode = sourceTree.nodeAt(i);
        TreeNode targetNode = targetTree.nodeAt(j);
        if (sourceNode.label().equals(targetNode.label())) {
          humandFriendlyMapping.add(
              String.format("No change for %s (@%d and @%d)",
                            sourceNode.label(),
                            sourceNode.preorderPosition(),
                            targetNode.preorderPosition()));
        }
        else {
          humandFriendlyMapping.add(
              String.format("Change from %s (@%d) to %s (@%d)",
                            sourceNode.label(),
                            sourceNode.preorderPosition(),
                            targetNode.label(),
                            targetNode.preorderPosition()));
        }
      }
    }
    return humandFriendlyMapping;
  }

  /**
   * Returns the distance between the given trees and the list of pairs
   * where each pair (x, y) shows which node at the preorder position x
   * in the source tree is mapped to which node at the preorder
   * position y in the target tree. If x is ALPHA, then it shows
   * the node at the preorder position y in the target tree is
   * inserted. If y is ALPHA, then it shows the node at the preorder
   * position x in the souce tree is deleted.
   *
   * @parameter sourceTree the source tree (Tree)
   * @parameter targetTree the target tree (Tree)
   * @returns (int, [(int, int)])
   */
  public static Result computeDiff(Tree sourceTree, Tree targetTree) {
    // E, mappingForE
    DictionaryPair dictionaryPair = computeE(sourceTree, targetTree);
    // MIN_M, mappingForMinM
    dictionaryPair = computeMIN_M(dictionaryPair, sourceTree, targetTree);
    // D, mappingForD
    dictionaryPair = computeD(sourceTree, targetTree, dictionaryPair);
    Map<String, Integer> D = dictionaryPair.costs;
    Map<String, MappingList> mappingForD = dictionaryPair.mapping;
    MappingList mapping =
        mappingForD.get(keyForD(sourceTree.size(), targetTree.size()));
    // mapping.sort();
    return new Result(
        D.get(keyForD(sourceTree.size(), targetTree.size())),
        mapping);
  }
}

class DictionaryPair {
  Map<String, Integer> costs;
  Map<String, MappingList> mapping;
  public DictionaryPair(
      Map<String, Integer> costs,
      Map<String, MappingList> mapping) {
    this.costs = costs;
    this.mapping = mapping;
  }
}

class IntPair {
  int x, y;
  public IntPair(int x, int y) {
    this.x = x;
    this.y = y;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof IntPair) {
      IntPair ip = (IntPair) o;
      return ip.x == x && ip.y == y;
    }
    return super.equals(o);
  }

  @Override
  public String toString() {
    return "(" + x + ", " + y + ")";
  }
}

class MappingList { // implements Iterable<IntPair>{
  ArrayList<IntPair> mList = new ArrayList<>();

  public MappingList() {}

  public MappingList(int x, int y) {
    add(x, y);
  }

  // public Iterator<IntPair> iterator() {
  //   return mList.iterator();
  // }

  public MappingList add(int x, int y) {
    mList.add(new IntPair(x, y));
    return this;
  }

  public MappingList clone() {
    MappingList m = new MappingList();
    for (IntPair pair : mList) {
      m.mList.add(pair);
    }
    return m;
  }

  public boolean contains(IntPair ip) {
    for (IntPair pair : mList) {
      if (pair.equals(ip)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof MappingList) {
      ArrayList<IntPair> list = ((MappingList) o).mList;
      if (mList.size() != list.size()) {
        return false;
      }
      for (IntPair ip : list) {
        if (!mList.contains(ip)) {
          return false;
        }
      }
      return true;
    }
    return super.equals(o);
  }

  public MappingList extendWith(MappingList list) {
    for (IntPair ip : list.mList) {
      mList.add(ip);
    }
    return this;
  }

  public MappingList extendUniquelyWith(MappingList list) {
    for (IntPair ip : list.mList) {
      if (mList.contains(ip)) {
        continue;
      }
      mList.add(ip);
    }
    return this;
  }

  public String toString() {
    return mList.toString();
  }
}

class Result {
  int cost;
  MappingList mapping;

  public Result(int cost, MappingList mapping) {
    this.cost = cost;
    this.mapping = mapping;
  }
}