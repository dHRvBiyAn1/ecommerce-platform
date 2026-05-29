import * as React from "react";
import { useNavigate } from "react-router-dom";
import { useAuthStore } from "@/stores/auth";
import { useCart } from "@/stores/cart";
import { me } from "@/api/auth";
import { PageSpinner } from "@/components/ui/spinner";
import { toast } from "sonner";

/**
 * Landing page for the OAuth2 redirect.
 *
 * The backend's CustomOAuth2SuccessHandler redirects here as:
 *   http://localhost:5173/oauth2/redirect#token=<jwt>
 *
 * We extract the token from the URL fragment (never sent to the server),
 * store it, fetch the user profile, then navigate to the intended destination.
 */
export const OAuth2RedirectPage: React.FC = () => {
  const navigate = useNavigate();
  const setAccessToken = useAuthStore((s) => s.setAccessToken);
  const setUser = useAuthStore((s) => s.setUser);
  const fetchCart = useCart((s) => s.fetch);

  const processingRef = React.useRef(false);

  React.useEffect(() => {
    if (processingRef.current) return;
    processingRef.current = true;

    const fragment = window.location.hash.slice(1); // strip leading #
    const params = new URLSearchParams(fragment);
    const token = params.get("token");

    if (!token) {
      toast.error("OAuth2 login failed — no token received.");
      navigate("/login", { replace: true });
      return;
    }

    setAccessToken(token);

    Promise.all([me(), fetchCart()])
      .then(([profile]) => {
        setUser(profile);
        toast.success(`Welcome, ${profile.displayName ?? profile.email}`);
        navigate("/", { replace: true });
      })
      .catch(() => {
        toast.error("Could not load your profile. Please try again.");
        navigate("/login", { replace: true });
      });
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  return <PageSpinner label="Signing you in…" />;
};
