export default class Utils {
  static isMobileDevice(): boolean {
    const deviceNavigatorList: string[] = [
      "Android",
      "webOS",
      "iPhone",
      "iPad",
      "iPod",
      "Blackberry",
      "Windows Phone",
    ];
    for (let i = 0; i < deviceNavigatorList.length; i++) {
      if (window.navigator.userAgent.match(deviceNavigatorList[i])) {
        return true;
      }
    }
    return false;
  }
}
