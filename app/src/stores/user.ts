import { reactive } from "vue";
import type { Profile } from "../utils/types";
import { clearToken, setToken } from "../utils/api";

interface UserState {
  token: string;
  profile: Profile | null;
}

function load(): UserState {
  try {
    return uni.getStorageSync("lingo_user") || { token: "", profile: null };
  } catch (e) {
    return { token: "", profile: null };
  }
}

export const user = reactive<UserState>(load());

export function setAuth(token: string, profile: Profile) {
  setToken(token);
  user.token = token;
  user.profile = profile;
  uni.setStorageSync("lingo_user", { token, profile });
}

export function updateProfile(profile: Profile) {
  user.profile = profile;
  uni.setStorageSync("lingo_user", { token: user.token, profile });
}

export function clearAuth() {
  clearToken();
  user.token = "";
  user.profile = null;
  uni.removeStorageSync("lingo_user");
}

export function isLoggedIn(): boolean {
  return !!user.token;
}

/** 未登录跳登录页；已登录但没完成 onboarding 时引导流程 */
export function ensureAuth(): boolean {
  if (!isLoggedIn()) {
    uni.reLaunch({ url: "/pages/login/login" });
    return false;
  }
  const step = user.profile?.onboardingStep;
  if (step === "goal") {
    uni.redirectTo({ url: "/pages/onboarding/onboarding" });
    return false;
  }
  if (step === "placement") {
    uni.redirectTo({ url: "/pages/placement/placement" });
    return false;
  }
  return true;
}
