import * as cdk from "aws-cdk-lib";
import * as constructs from "constructs";
import * as cloudwatch from "aws-cdk-lib/aws-cloudwatch";

export class DashboardStack extends cdk.Stack {
  constructor(scope: constructs.Construct, id: string, props: cdk.StackProps) {
    super(scope, id, props);

    const vastaanottoNormaaliMetric = new cloudwatch.Metric({
      metricName: "VastaanottojenMaara",
      namespace: `viestinvalitys`,
      statistic: cloudwatch.Stats.SUM,
      dimensionsMap: { Prioriteetti: "NORMAALI" },
      label: "Vastaanotot Normaali",
      period: cdk.Duration.seconds(10),
    });
    const vastaanottoKorkeaMetric = new cloudwatch.Metric({
      metricName: "VastaanottojenMaara",
      namespace: `viestinvalitys`,
      statistic: cloudwatch.Stats.SUM,
      dimensionsMap: { Prioriteetti: "KORKEA" },
      label: "Vastaanotot Korkea",
      period: cdk.Duration.seconds(10),
    });

    const lahetysNormaaliMetric = new cloudwatch.Metric({
      metricName: "LahetyksienMaara",
      namespace: `viestinvalitys`,
      statistic: cloudwatch.Stats.SUM,
      dimensionsMap: { Prioriteetti: "NORMAALI" },
      label: "Lähetykset Normaali",
      period: cdk.Duration.seconds(10),
    });
    const lahetysKorkeaMetric = new cloudwatch.Metric({
      metricName: "LahetyksienMaara",
      namespace: `viestinvalitys`,
      statistic: cloudwatch.Stats.SUM,
      dimensionsMap: { Prioriteetti: "KORKEA" },
      label: "Lähetykset Korkea",
      period: cdk.Duration.seconds(10),
    });

    const lahetysWidget = new cloudwatch.GraphWidget({
      width: 24,
      title: "Vastaanotot ja Lähetykset per/10s",
      left: [
        vastaanottoNormaaliMetric,
        vastaanottoKorkeaMetric,
        lahetysNormaaliMetric,
        lahetysKorkeaMetric,
      ],
    });

    const dashboard = new cloudwatch.Dashboard(this, "Dashboard", {
      dashboardName: `viestinvalitys`,
    });
    dashboard.addWidgets(lahetysWidget);
  }
}
