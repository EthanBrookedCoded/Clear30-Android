"use client";

import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from "recharts";
import { format } from "date-fns";

interface CreatorsChartProps {
  data: { date: string; count: number }[];
  height?: number;
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr);
  return format(date, "MMM d");
}

function formatTooltipDate(dateStr: string): string {
  const date = new Date(dateStr);
  return format(date, "MMM d, yyyy");
}

interface TooltipPayload {
  value: number;
}

interface CustomTooltipProps {
  active?: boolean;
  payload?: TooltipPayload[];
  label?: string;
}

function CustomTooltip({ active, payload, label }: CustomTooltipProps) {
  if (!active || !payload || !label) return null;

  return (
    <div className="bg-white border border-gray-200 rounded-lg shadow-lg p-3">
      <p className="text-sm text-gray-600 mb-1">{formatTooltipDate(label)}</p>
      <p className="text-sm font-medium text-blue-600">
        Creators Added: {payload[0]?.value || 0}
      </p>
    </div>
  );
}

export function CreatorsChart({ data, height }: CreatorsChartProps) {
  if (!data || data.length === 0) {
    return (
      <div
        className="flex items-center justify-center text-gray-500 h-full"
        style={height ? { height } : undefined}
      >
        No data available for this period
      </div>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={height || "100%"}>
      <LineChart
        data={data}
        margin={{ top: 5, right: 20, left: 10, bottom: 5 }}
      >
        <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
        <XAxis
          dataKey="date"
          tickFormatter={formatDate}
          stroke="#6b7280"
          fontSize={12}
          tickMargin={8}
        />
        <YAxis
          stroke="#6b7280"
          fontSize={12}
          tickMargin={8}
          width={40}
          allowDecimals={false}
        />
        <Tooltip content={<CustomTooltip />} />
        <Line
          type="monotone"
          dataKey="count"
          name="Creators Added"
          stroke="#2563eb"
          strokeWidth={2}
          dot={{ fill: "#2563eb", strokeWidth: 0, r: 3 }}
          activeDot={{ r: 5, strokeWidth: 0 }}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
