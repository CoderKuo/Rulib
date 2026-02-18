package com.dakuo.rulib.common.cmd.reload

import taboolib.common.LifeCycle
import taboolib.common.inject.ClassVisitor
import taboolib.common.platform.Awake
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.component.CommandComponent
import taboolib.common.platform.function.submitAsync
import taboolib.library.reflex.ClassField
import taboolib.library.reflex.ClassMethod
import taboolib.library.reflex.ReflexClass
import taboolib.module.configuration.Configuration

@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Reload(val priority: Int = 10)

@Awake
object ReloadExecutor : ClassVisitor(-10) {

    private val reloadMethods = mutableSetOf<Triple<ClassMethod, Any, Int>>()
    private val reloadConfiguration = mutableSetOf<Triple<Configuration, Any, Int>>()

    override fun visit(field: ClassField, owner: ReflexClass) {
        if (field.isAnnotationPresent(Reload::class.java)) {
            val priority = field.getAnnotation(Reload::class.java).property<Int>("priority") ?: 10
            owner.getInstance()?.let { instance ->
                reloadConfiguration.add(Triple(field.get(instance) as Configuration, instance, priority))
            }
        }
    }


    override fun visit(method: ClassMethod, owner: ReflexClass) {
        if (method.isAnnotationPresent(Reload::class.java)) {
            val priority = method.getAnnotation(Reload::class.java).property<Int>("priority") ?: 10
            owner.getInstance()?.let { instance ->
                reloadMethods.add(Triple(method, instance, priority))
            }
        }
    }

    fun execute() {
        reloadConfiguration.sortedBy { it.third }.forEach { it.first.reload() }
        reloadMethods
            .sortedBy { it.third }
            .forEach { it.first.invoke(it.second) }
    }

    override fun getLifeCycle(): LifeCycle {
        return LifeCycle.ACTIVE
    }

}

fun CommandComponent.reload() {
    exec<ProxyCommandSender> {
        submitAsync {
            runCatching {
                ReloadExecutor.execute()
            }.onSuccess {
                sender.sendMessage("重载成功")
            }.onFailure {
                sender.sendMessage("重载失败: ${it.message}")
                it.printStackTrace()
            }
        }
    }
}