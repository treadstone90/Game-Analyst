object Main {
  def main(args: Array[String]) {
    val str1 = "joakim noah , bulls beat nets in game 7 ; heat are next ...";
    val str2 = "joakim noah , bulls beat nets in game 7 ; heat are next ...";

    val shingledStr1 = str1.toList.sliding(5).toSet
    val shingledStr2 = str2.toList.sliding(5).toSet
    println(shingledStr1);
    println(shingledStr2);
    println("sadf");
    val inter = shingledStr1.intersect(shingledStr2);
    val uni = shingledStr1.union(shingledStr2);

    println((1.0 * inter.size / uni.size));
    println(uni.size);

  }
}