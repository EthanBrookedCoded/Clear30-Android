class RemoteService {
  videoRoutes = {
    whosCallingTheShots: "https://m.clear30.org/whos-calling-the-shots.mp4",
    whosCallingTheShotsThumbnail:
      "https://m.clear30.org/whos-calling-the-shots.png",
    therapy: "https://m.clear30.org/therapy.mp4",
    therapyThumbnail: "https://m.clear30.org/therapy.webp",
    claire: "https://m.clear30.org/claire.mp4",
    claireThumbnail: "https://m.clear30.org/claire.webp",
    meditation: "https://m.clear30.org/meditation.mp4",
    meditationThumbnail: "https://m.clear30.org/meditation.png",
  };

  pageRoute = "./assets/all_programs.json";

  audioRoutes = {
    test: "https://m.clear30.org/clear30-test.mp3",
    presignup: "https://m.clear30.org/presignupv1.mp3",
  };

  errorCodes = {
    NO_RESULTS: "NO_RESULTS",
    FAILED_REQUEST: "FAILED_REQUEST",
  };

  pageData: any;
  pageDataPromise: Promise<any> | undefined;
  public loadPageData() {
    if (this.pageData) {
      return Promise.resolve(this.pageData);
    } else if (this.pageDataPromise) {
      return this.pageDataPromise;
    } else {
      this.pageDataPromise = this.loadPageDataHelper();
      return this.pageDataPromise;
    }
  }

  async loadPageDataHelper(): Promise<any> {
    try {
      const rawJson: Response = await fetch(this.pageRoute, {
        mode: "cors",
        cache: "no-cache",
      });
      if (!rawJson) {
        throw new Error("Could not fetch program data");
      }
      this.pageData = await rawJson.json();
      return Promise.resolve(this.pageData);
    } catch (err) {
      console.error(err);
      return;
    }
  }

  extractBackendJson(results: string) {
    let startInd = -1,
      endInd = -1;
    for (let i = 0; i < results.length; i++) {
      if (results[i] === "{") {
        startInd = i;
        break;
      }
    }

    for (let i = results.length - 1; i >= 0; i--) {
      if (results[i] === "}") {
        endInd = i;
        break;
      }
    }

    return JSON.parse(results.substring(startInd, endInd + 1));
  }

  unescape(escapedHtml: string): string {
    escapedHtml = escapedHtml.replace(/&lt;/g, "<");
    escapedHtml = escapedHtml.replace(/&gt;/g, ">");
    escapedHtml = escapedHtml.replace(/&quot;/g, '"');
    escapedHtml = escapedHtml.replace(/&#39;/g, "'");
    escapedHtml = escapedHtml.replace(/&amp;/g, "&");
    return escapedHtml;
  }
}

const service = new RemoteService();
export default service;
