int main() {
    int a;
    int b;
    float c;

    a = 2 + 3;    // constant fold to a = 5;
    b = (int)(4.0 * 2.0); // constant fold to b = (int)(8.0), i.e., b = 8
    c = 10.0 / 2.0;  // constant fold to c = 5.0

    print(a); // should print 5
    print(b); // should print 8
    print(c); // should print 5.0

    // More complex expression:
    // (3 * 4) + (2 * 5) = 12 + 10 = 22
    a = (3 * 4) + (2 * 5);
    print(a); // should print 22

    return 0;
}
