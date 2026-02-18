package com.dakuo.rulib.common.action

import taboolib.common.LifeCycle
import taboolib.common.inject.ClassVisitor
import taboolib.common.platform.Awake
import taboolib.library.configuration.ConfigurationSection
import taboolib.library.reflex.ReflexClass
import taboolib.module.configuration.Configuration
import java.util.concurrent.ConcurrentHashMap

annotation class ActionParent(val groupName: String)

annotation class Action(val main: String, val alias: Array<String> = [])

@Awake
object ActionLoader : ClassVisitor(-50) {

    val actions = ConcurrentHashMap<String, MutableMap<String, ReflexClass>>()

    override fun visitEnd(clazz: ReflexClass) {
        if (clazz.hasAnnotation(Action::class.java)) {
            clazz.interfaces.filter {
                it.hasAnnotation(ActionParent::class.java)
            }.forEach {
                val groupName = it.getAnnotation(ActionParent::class.java).property<String>("groupName")
                clazz.getAnnotationIfPresent(Action::class.java)?.also {
                    val main = it.property<String>("main")!!
                    val alias = it.list<String>("alias")
                    actions.getOrPut(groupName) {
                        mutableMapOf()
                    }.also {
                        it.put(main, clazz)
                        alias.forEach { n ->
                            it.put(n, clazz)
                        }
                    }
                }
            }
        }

    }

    override fun getLifeCycle(): LifeCycle {
        return LifeCycle.LOAD
    }

}

inline fun <reified T> buildAction(name: String, vararg params: Any?): T? {
    return ActionLoader.actions[T::class.java.getAnnotation(ActionParent::class.java).groupName]?.get(name)
        ?.newInstance(*params) as? T
}

inline fun <reified T> ConfigurationSection.getActions(key: String): List<T> {
    return getMapList(key).map {
        Configuration.fromMap(it)
    }.mapNotNull {
        buildAction<T>(it.getString("type")!!, it)
    }
}