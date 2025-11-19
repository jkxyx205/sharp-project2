package com.rick.test.module.lombok;

/**
 * @author Rick.Xu
 * @date 2025/11/18 15:13
 */
public class Test {
    public static void main(String[] args) {
        MyData build = MyData.builder().id(100).build();
        System.out.println(build.id);
//        build.toBuilder //

        MyData2 build1 = MyData2.br().sId(101).bd();
        System.out.println(build1.id);

//        当你启用 toBuilder = true 时
//        这个方法会：
//        基于当前对象已有字段值
//        构造一个带初始值的 builder
//        让你在现有对象基础上改少量字段
//        相当于是 copy / clone + builder 的组合。
        build1.toBuilder().sId(23);

        MyData.MyDataBuilder<?, ?> builder = MyData.builder();
        MyData.MyDataBuilder<?, ?> id = builder.id(12);

        MyData2.t br = MyData2.br();
        MyData2.t t = br.sId(23);

        SubClass.builder().id(1).subName("234").build();
//        SubClass2.builder().id(2).subName("234").build();

//        ✅ 规则（非常重要）
//        父类注解	子类注解	结果
//        @Builder	@SuperBuilder	❌ 子类不能 builder 父类字段
//        @SuperBuilder	@SuperBuilder	✔ 子类 builder 继承父类字段
//        @Builder	@Builder	❌ builder 不继承
//        @SuperBuilder	@Builder	❌ 不能组合

//        @SuperBuilder 需要 父类也使用 @SuperBuilder 才能继承父类 builder 信息。
    }
}
