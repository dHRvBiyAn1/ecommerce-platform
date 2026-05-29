import { Skeleton } from "@/components/ui/skeleton";

export function ProductDetailSkeleton() {
  return (
    <div className="container pb-28 pt-6 sm:py-12 md:pb-16">
      <Skeleton className="h-4 w-32" />
      <div className="mt-4 grid gap-8 sm:mt-6 lg:grid-cols-2 lg:gap-12">
        <div className="grid grid-cols-2 gap-3">
          <Skeleton className="col-span-2 aspect-[3/4] w-full rounded-xl" />
          <Skeleton className="aspect-square w-full rounded-xl" />
          <Skeleton className="aspect-square w-full rounded-xl" />
        </div>
        <div className="space-y-6">
          <div className="space-y-2">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="h-12 w-3/4" />
          </div>
          <div className="flex gap-4">
            <Skeleton className="h-8 w-24" />
            <Skeleton className="h-8 w-24" />
          </div>
          <div className="space-y-2">
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-2/3" />
          </div>
          <div className="flex gap-4">
            <Skeleton className="h-12 w-32" />
            <Skeleton className="h-12 flex-1" />
            <Skeleton className="h-12 w-12" />
          </div>
          <div className="grid gap-2 sm:grid-cols-2">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
          </div>
        </div>
      </div>
    </div>
  );
}
