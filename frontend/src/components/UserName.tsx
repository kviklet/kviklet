/**
 * A user's display name. Deactivated accounts are grayed out everywhere they appear
 * (request lists, review pages, filters, API keys) so historical records stay readable
 * while making clear the person no longer has access.
 */
const UserName = (props: {
  user: { fullName?: string | null; email?: string; active?: boolean };
  className?: string;
}) => {
  const name = props.user.fullName || props.user.email;
  if (props.user.active === false) {
    return (
      <span
        className={`opacity-60 ${props.className ?? ""}`}
        title="This account has been deactivated"
      >
        {name}
      </span>
    );
  }
  return <span className={props.className}>{name}</span>;
};

export default UserName;
