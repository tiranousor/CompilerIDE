class Main {
    public static void main(String[] args) {
        System.out.println("Hello W");
        Main2 main2 = new Main2();
        Main3 main3 = new Main3();
        main3.setB("pr");
        main3.setC(1234);
        System.out.println(main3.getB() + " " + main3.getC()+main2.a);
    }
}
