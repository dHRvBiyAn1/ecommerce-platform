import * as React from "react";
import { Link } from "react-router-dom";
import { Frown } from "lucide-react";
import { Button } from "@/components/ui/button";

export const NotFoundPage: React.FC = () => (
  <div className="grid min-h-screen place-items-center bg-background p-6 text-center">
    <div>
      <Frown className="mx-auto h-12 w-12 text-muted-foreground/50" />
      <p className="mt-6 font-display text-7xl font-semibold tracking-tight">404</p>
      <p className="mt-2 text-sm text-muted-foreground">
        That page didn't make it past the loading dock.
      </p>
      <Button asChild variant="accent" className="mt-6">
        <Link to="/">Take me home</Link>
      </Button>
    </div>
  </div>
);
