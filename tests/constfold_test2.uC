float main() {
    float x;
    x = 3.14 * 2.0;   // folds to 6.28
    print(x); // should print 6.28

    x = (float)(5 + 5); // folds to (float)(10), x = 10.0
    print(x); // should print 10.0

    return x; // returns 10.0
}
