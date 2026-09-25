use adblock::lists::{FilterSet, ParseOptions};
use adblock::request::Request;
use adblock::resources::{PermissionMask, Resource};
use adblock::Engine;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use std::sync::{OnceLock, RwLock};

static ENGINE: OnceLock<RwLock<Option<Engine>>> = OnceLock::new();

fn engine_store() -> &'static RwLock<Option<Engine>> {
    ENGINE.get_or_init(|| RwLock::new(None))
}

fn java_string(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Option<String> {
    env.get_string(value).ok().map(|s| s.into())
}

#[no_mangle]
pub extern "system" fn Java_com_tihulu_tvlite_BraveAdblock_nativeInit(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    untrusted_rules: JString<'_>,
    brave_rules: JString<'_>,
    resources_json: JString<'_>,
) -> jboolean {
    let Some(untrusted_rules) = java_string(&mut env, &untrusted_rules) else {
        return JNI_FALSE;
    };
    let Some(brave_rules) = java_string(&mut env, &brave_rules) else {
        return JNI_FALSE;
    };
    let Some(resources_json) = java_string(&mut env, &resources_json) else {
        return JNI_FALSE;
    };

    let mut filter_set = FilterSet::new(false);
    filter_set.add_filter_list(untrusted_rules, ParseOptions::default());

    // Brave-authored rules can reference resources requiring Brave's permission bit.
    filter_set.add_filter_list(
        brave_rules,
        ParseOptions {
            permissions: PermissionMask::from_bits(0b0000_0010),
            ..ParseOptions::default()
        },
    );

    let mut engine = Engine::new_with_filter_set(filter_set);

    if let Ok(resources) = serde_json::from_str::<Vec<Resource>>(&resources_json) {
        engine.use_resources(resources);
    }

    match engine_store().write() {
        Ok(mut slot) => {
            *slot = Some(engine);
            JNI_TRUE
        }
        Err(_) => JNI_FALSE,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_tihulu_tvlite_BraveAdblock_nativeShouldBlock(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    url: JString<'_>,
    source_url: JString<'_>,
    request_type: JString<'_>,
    method: JString<'_>,
) -> jboolean {
    let Some(url) = java_string(&mut env, &url) else {
        return JNI_FALSE;
    };
    let Some(source_url) = java_string(&mut env, &source_url) else {
        return JNI_FALSE;
    };
    let Some(request_type) = java_string(&mut env, &request_type) else {
        return JNI_FALSE;
    };
    let Some(method) = java_string(&mut env, &method) else {
        return JNI_FALSE;
    };

    let Ok(request) = Request::new(&url, &source_url, &request_type, &method) else {
        return JNI_FALSE;
    };

    let Ok(slot) = engine_store().read() else {
        return JNI_FALSE;
    };
    let Some(engine) = slot.as_ref() else {
        return JNI_FALSE;
    };

    let result = engine.check_network_request(&request);
    if result.matched {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_tihulu_tvlite_BraveAdblock_nativeCosmeticResources(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    url: JString<'_>,
) -> jstring {
    let Some(url) = java_string(&mut env, &url) else {
        return std::ptr::null_mut();
    };

    let Ok(slot) = engine_store().read() else {
        return std::ptr::null_mut();
    };
    let Some(engine) = slot.as_ref() else {
        return std::ptr::null_mut();
    };

    let resources = engine.url_cosmetic_resources(&url);
    let Ok(json) = serde_json::to_string(&resources) else {
        return std::ptr::null_mut();
    };
    let Ok(output) = env.new_string(json) else {
        return std::ptr::null_mut();
    };
    output.into_raw()
}
