package org.lynxz.utils.reflect

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 通过反射,生成动态代理类的基本结构信息
 * 通过传入 class 对象, 然后调用 [generate] 来生成对应class字符串内容
 */
class ProxyGeneratorImpl(val clz: Class<Any>) {
    companion object {
        private const val TAG = "ProxyGeneratorImpl"
    }

    /**
     * 返回该class文件的签名信息, 包含类签名,成员变量签名, 方法签名等
     */
    fun generate(): String {
        val sb = StringBuilder()
        with(clz) {
            // 获取类签名信息
            sb.append(convertModify2Str(modifiers))
                .append(" class ").append(name)
                .append(" extends ").append("${superclass?.name}")
                .append(" implements ")
            interfaces.forEach {
                sb.append("${it.simpleName},")
            }
            sb.append("{\n")

            // 获取所有成员变量
            declaredFields.forEach { sb.append("\t").append(assembleFieldSig(it)).append("\n") }
            // 获取所有构造方法
            constructors.forEach { sb.append("\t").append(assembleConstructorSig(it)).append("\n") }
            // 获取所有方法签名
            declaredMethods.forEach { sb.append("\t").append(assembleMethodSig(it)).append("\n") }

            sb.append("}")

            return sb.toString()
                .replace(",)", ")")
                .replace(",{", "{")
        }
    }

    /**
     * 转换modifier数字为字符串
     */
    private fun convertModify2Str(modifiers: Int): String {
        val sb = StringBuilder()
        sb.append(
            when {
                Modifier.isPublic(modifiers) -> " public "
                Modifier.isProtected(modifiers) -> " protected "
                Modifier.isPrivate(modifiers) -> " private "
                else -> ""
            }
        )

        if (Modifier.isStatic(modifiers)) {
            sb.append(" static ")
        }

        if (Modifier.isFinal(modifiers)) {
            sb.append(" final ")
        }

        if (Modifier.isAbstract(modifiers)) {
            sb.append(" abstract ")
        }
        return sb.toString().replace("  ", " ").trim()
    }

    /**
     * 生成变量签名信息
     */
    private fun assembleFieldSig(field: Field) =
        "${convertModify2Str(field.modifiers)} ${field.type.simpleName} ${field.name};"

    /**
     * 生成方法签名信息
     */
    private fun assembleMethodSig(method: Method): String {
        val sb = StringBuilder()
        sb.append(convertModify2Str(method.modifiers))
            .append(" ")
            .append("${method.returnType.simpleName} ${method.name}(")
        method.parameterTypes.forEach { para ->
            sb.append(para.simpleName).append(",")
        }
        sb.append("){...}")
        return sb.toString().replace(",)", ")")
    }

    private fun assembleConstructorSig(cons: Constructor<*>): String {
        val sb = StringBuilder()
        sb.append(convertModify2Str(cons.modifiers))
            .append(" ")
            .append(cons.name)
            .append("(")

        cons.genericParameterTypes.forEach {
            sb.append(it.javaClass.simpleName).append(",")
        }

        sb.append("){...}")
        return sb.toString().replace(",)", ")")
    }
}