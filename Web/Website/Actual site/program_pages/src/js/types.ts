interface ProgramPage {
  title: string;
  quote?: string;
  sections: Array<
    | TextSection
    | VideoSection
    | ReferenceSection
    | ImageSection
    | AudioSection
    | ShareSection
    | ResourceSection
  >;
}

interface ReferenceSection {
  type: "ReferenceSection";
  header?: string;
  references: Array<Reference>;
}

interface Reference {
  type: "scientific" | "external";
  description: string;
  link?: string;
}

interface AudioSection {
  type: "AudioSection" | "audio";
  header?: string;
  sourcePath?: string;
  link?: string;
}

interface ResourceSection {
  type: "resources";
  header?: string;
  resources: any;
}

interface TextSection {
  type: "TextSection" | "text";
  header?: string;
  paragraphs: Array<
    ListParagraph | TextParagraph | NumberedListParagraph | RouteParagraph
  >;
}
interface ListParagraph {
  type: "list";
  header?: string;
  list: string[];
  styling?: string;
}

interface NumberedListParagraph {
  type: "numbered-list";
  header?: string;
  list: string[];
}

interface TextParagraph {
  type: "text";
  header?: string;
  styling?: string;
  content: string;
}

interface RouteParagraph {
  type: "route";
  header?: string;
  route: string;
  routeText: string;
  styling?: string;
  content?: string;
}

interface VideoSection {
  type: "VideoSection";
  sourcePath: string;
}

interface ImageSection {
  type: "ImageSection" | "image";
  sourcePath?: string;
  link?: string;
}

interface ShareSection {
  type: "ShareSection";
  shareDescription: string;
  shareTitle: string;
}

interface Settings {
  serverUrl: string;
  paymentLink: string;
}

export {
  ProgramPage,
  ReferenceSection,
  VideoSection,
  TextSection,
  ShareSection,
  Settings,
  ResourceSection,
};
