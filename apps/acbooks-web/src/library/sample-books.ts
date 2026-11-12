export type BookTag = "epub" | "pdf" | "physical";

export type ReadingStatus = "done" | "reading" | "unread";

export interface LibraryBook {
  author: string;
  cover: {
    background: string;
    foreground: string;
    line: string;
  };
  id: string;
  progress?: number;
  status: ReadingStatus;
  tags: readonly BookTag[];
  title: string;
}

const DARK_LINE = "rgba(23, 32, 51, 0.22)";
const LIGHT_LINE = "rgba(255, 255, 255, 0.28)";

// Design-artboard sample data until the books API is available.
export const SAMPLE_BOOKS: readonly LibraryBook[] = [
  {
    author: "鲁迅",
    cover: { background: "#d9c8b4", foreground: "#3a2c1e", line: DARK_LINE },
    id: "nahan",
    status: "done",
    tags: ["physical", "epub"],
    title: "呐喊",
  },
  {
    author: "沈从文",
    cover: { background: "#bcd6c9", foreground: "#1f3a2e", line: DARK_LINE },
    id: "biancheng",
    progress: 63,
    status: "reading",
    tags: ["epub"],
    title: "边城",
  },
  {
    author: "路遥",
    cover: { background: "#e9d49a", foreground: "#4a3a0c", line: DARK_LINE },
    id: "pingfan-de-shijie",
    progress: 18,
    status: "reading",
    tags: ["physical", "epub", "pdf"],
    title: "平凡的世界",
  },
  {
    author: "刘慈欣",
    cover: { background: "#1f2a44", foreground: "#e8edf5", line: LIGHT_LINE },
    id: "santi",
    status: "unread",
    tags: ["epub"],
    title: "三体",
  },
  {
    author: "尤瓦尔·赫拉利",
    cover: { background: "#f0b9a3", foreground: "#4a2214", line: DARK_LINE },
    id: "renlei-jianshi",
    status: "unread",
    tags: ["physical"],
    title: "人类简史",
  },
  {
    author: "唐纳德·诺曼",
    cover: { background: "#c9d6f2", foreground: "#1b2e5c", line: DARK_LINE },
    id: "sheji-xinlixue",
    progress: 41,
    status: "reading",
    tags: ["physical", "pdf"],
    title: "设计心理学",
  },
  {
    author: "圣埃克苏佩里",
    cover: { background: "#a9d3df", foreground: "#12414f", line: DARK_LINE },
    id: "xiao-wangzi",
    status: "done",
    tags: ["epub"],
    title: "小王子",
  },
  {
    author: "毛姆",
    cover: { background: "#2f3b3a", foreground: "#f1e8d0", line: LIGHT_LINE },
    id: "yueliang-yu-liubianshi",
    status: "unread",
    tags: ["physical"],
    title: "月亮与六便士",
  },
  {
    author: "加西亚·马尔克斯",
    cover: { background: "#e4a85b", foreground: "#3d2406", line: DARK_LINE },
    id: "bainian-gudu",
    status: "unread",
    tags: ["epub"],
    title: "百年孤独",
  },
  {
    author: "余华",
    cover: { background: "#c6c2bb", foreground: "#2b2b28", line: DARK_LINE },
    id: "huozhe",
    status: "done",
    tags: ["physical", "epub"],
    title: "活着",
  },
  {
    author: "村上春树",
    cover: { background: "#7d9bd4", foreground: "#0f1f45", line: DARK_LINE },
    id: "haibian-de-kafuka",
    progress: 7,
    status: "reading",
    tags: ["epub"],
    title: "海边的卡夫卡",
  },
  {
    author: "Randal E. Bryant",
    cover: { background: "#e7e9ee", foreground: "#1b2230", line: DARK_LINE },
    id: "csapp",
    status: "unread",
    tags: ["pdf"],
    title: "深入理解计算机系统",
  },
];
