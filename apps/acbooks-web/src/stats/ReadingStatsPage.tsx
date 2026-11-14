import { useTranslation } from "react-i18next";
import { PageContainer } from "../layout/PageContainer";
import { PageHeader } from "../layout/PageHeader";
import { useDocumentTitle } from "../layout/use-document-title";
import ReadingStatsPanel from "./ReadingStatsPanel";

export default function ReadingStatsPage() {
  const { t } = useTranslation("stats");
  useDocumentTitle(t("title"));
  return <PageContainer>
    <div className="space-y-4">
      <PageHeader title={t("title")} />
      <ReadingStatsPanel />
    </div>
  </PageContainer>;
}
