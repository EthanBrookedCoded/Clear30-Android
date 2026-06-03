"use client";

import * as React from "react";
import {
  ColumnDef,
  SortingState,
  PaginationState,
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  getPaginationRowModel,
  useReactTable,
} from "@tanstack/react-table";

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { ChevronLeft, ChevronRight } from "lucide-react";

interface CreatorsTableProps<TData, TValue> {
  columns: ColumnDef<TData, TValue>[];
  data: TData[];
  onRowClick?: (row: TData) => void;
  isRowSelected?: (row: TData) => boolean;
  emptyMessage?: string;
}

export function CreatorsTable<TData, TValue>({
  columns,
  data,
  onRowClick,
  isRowSelected,
  emptyMessage = "No creators added yet.",
}: CreatorsTableProps<TData, TValue>) {
  const [sorting, setSorting] = React.useState<SortingState>([]);

  const [pagination, setPagination] = React.useState<PaginationState>({
    pageIndex: 0,
    pageSize: 25,
  });

  const table = useReactTable({
    data,
    columns,
    getCoreRowModel: getCoreRowModel(),
    onSortingChange: setSorting,
    getSortedRowModel: getSortedRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    onPaginationChange: setPagination,
    autoResetPageIndex: false,
    state: {
      sorting,
      pagination,
    },
  });

  const pageCount = table.getPageCount();
  const currentPage = pagination.pageIndex;

  // Guard against invalid page index when data shrinks (e.g. filter reduces rows).
  React.useEffect(() => {
    if (pageCount > 0 && pagination.pageIndex >= pageCount) {
      table.setPageIndex(0);
    }
  }, [pageCount, pagination.pageIndex, table]);

  return (
    <div className="overflow-x-auto">
      <Table className="min-w-[600px]">
        <TableHeader>
          {table.getHeaderGroups().map((headerGroup) => (
            <TableRow key={headerGroup.id}>
              {headerGroup.headers.map((header) => {
                return (
                  <TableHead key={header.id}>
                    {header.isPlaceholder
                      ? null
                      : flexRender(
                          header.column.columnDef.header,
                          header.getContext()
                        )}
                  </TableHead>
                );
              })}
            </TableRow>
          ))}
        </TableHeader>
        <TableBody>
          {table.getRowModel().rows?.length ? (
            table.getRowModel().rows.map((row) => {
              const selected = isRowSelected?.(row.original) ?? false;
              return (
              <TableRow
                key={row.id}
                data-state={selected ? "selected" : undefined}
                className={cn(
                  onRowClick && "cursor-pointer",
                  selected &&
                    "bg-blue-50 hover:bg-blue-50 ring-2 ring-inset ring-blue-400"
                )}
                onClick={() => onRowClick?.(row.original)}
              >
                {row.getVisibleCells().map((cell) => (
                  <TableCell key={cell.id}>
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </TableCell>
                ))}
              </TableRow>
              );
            })
          ) : (
            <TableRow>
              <TableCell colSpan={columns.length} className="h-24 text-center">
                {emptyMessage}
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
      {pageCount > 1 && (
        <div className="flex flex-col sm:flex-row items-center justify-between gap-2 px-3 sm:px-4 py-3 border-t">
          <div className="text-xs sm:text-sm text-muted-foreground">
            Showing {currentPage * table.getState().pagination.pageSize + 1}-
            {Math.min((currentPage + 1) * table.getState().pagination.pageSize, table.getFilteredRowModel().rows.length)} of {table.getFilteredRowModel().rows.length}
          </div>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => table.previousPage()}
              disabled={!table.getCanPreviousPage()}
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <span className="text-xs sm:text-sm text-muted-foreground">
              {currentPage + 1} / {pageCount}
            </span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => table.nextPage()}
              disabled={!table.getCanNextPage()}
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
